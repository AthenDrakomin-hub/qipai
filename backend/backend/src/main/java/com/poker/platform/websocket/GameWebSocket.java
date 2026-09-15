package com.poker.platform.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.entity.User;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.service.RoomService;
import com.poker.platform.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间实时推送 WebSocket 端点（原型 30-36 各阶段实时驱动 + 43 聊天 / 44 表情）
 *
 * 连接：ws://host/ws/room/{roomId}?token=xxx （注意：WebSocket 端点不带 /api 前缀）
 *
 * 连接建立后服务端立即下发 TABLE_STATE 快照，供「断线重连」（原型 36）恢复牌桌。
 */
@Component
@ServerEndpoint("/ws/room/{roomId}")
public class GameWebSocket {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocket.class);

    /** roomId -> (userId -> Session) */
    private static final Map<String, Map<Long, Session>> ROOM_SESSIONS = new ConcurrentHashMap<>();

    private static JwtUtil jwtUtilStatic;
    private static RoomService roomServiceStatic;
    private static UserMapper userMapperStatic;

    @Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        GameWebSocket.jwtUtilStatic = jwtUtil;
    }

    @Autowired
    public void setRoomService(RoomService roomService) {
        GameWebSocket.roomServiceStatic = roomService;
    }

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        GameWebSocket.userMapperStatic = userMapper;
    }

    @OnOpen
    public void onOpen(@PathParam("roomId") String roomId, Session session) {
        String t = queryParam(session, "token");
        Long uid = (t != null && jwtUtilStatic != null) ? jwtUtilStatic.getUserId(t) : null;
        if (uid == null) {
            closeQuietly(session, "未登录");
            return;
        }
        session.getUserProperties().put("userId", uid);
        session.getUserProperties().put("roomId", roomId);
        ROOM_SESSIONS.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(uid, session);
        log.info("WS 加入: room={} uid={}", roomId, uid);

        // 连接建立后立即下发牌桌快照（原型 36 断线重连恢复牌桌）
        send(session, buildTableState(roomId).toJson());
    }

    @OnClose
    public void onClose(@PathParam("roomId") String roomId, Session session) {
        Long uid = (Long) session.getUserProperties().get("userId");
        if (uid != null) {
            Map<Long, Session> m = ROOM_SESSIONS.get(roomId);
            if (m != null) {
                m.remove(uid);
                if (m.isEmpty()) ROOM_SESSIONS.remove(roomId);
            }
            log.info("WS 离开: room={} uid={}", roomId, uid);
        }
    }

    /**
     * 客户端指令入口（原型 43 聊天 / 44 表情）
     * 入参 JSON：{ "type": "CHAT"|"EMOJI"|"PING", "content": "..." }
     */
    @OnMessage
    public void onMessage(@PathParam("roomId") String roomId, String msg, Session session) {
        Long uid = (Long) session.getUserProperties().get("userId");
        if (uid == null) return;

        String type = WsEvent.CMD_CHAT;
        String content = msg;
        try {
            JSONObject o = JSON.parseObject(msg);
            if (o != null) {
                if (o.getString("type") != null) type = o.getString("type").toUpperCase();
                if (o.getString("content") != null) content = o.getString("content");
            }
        } catch (Exception ignore) {
            // 非 JSON 文本，按聊天消息原样广播
        }

        if (WsEvent.CMD_PING.equals(type)) {
            send(session, WsEvent.of(WsEvent.PONG, null, roomId, "serverTime", System.currentTimeMillis()).toJson());
            return;
        }

        String nickname = nicknameOf(uid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("nickname", nickname);
        if (WsEvent.CMD_EMOJI.equals(type)) {
            data.put("emoji", content);
            broadcast(roomId, WsEvent.of(WsEvent.EMOJI, null, roomId, data));
        } else {
            data.put("content", content);
            broadcast(roomId, WsEvent.of(WsEvent.CHAT, null, roomId, data));
        }
    }

    @OnError
    public void onError(@PathParam("roomId") String roomId, Session session, Throwable e) {
        log.warn("WS 错误 room={}: {}", roomId, e.getMessage());
    }

    // ==================== 对外广播能力（供 Service / Controller 调用） ====================

    /** 广播原始文本 */
    public static void broadcast(String roomId, String msg) {
        Map<Long, Session> m = ROOM_SESSIONS.get(roomId);
        if (m == null) return;
        for (Session s : m.values()) {
            send(s, msg);
        }
    }

    /** 广播事件对象 */
    public static void broadcast(String roomId, WsEvent event) {
        if (event != null) broadcast(roomId, event.toJson());
    }

    /** 给指定用户单发 */
    public static void sendToUser(String roomId, Long userId, WsEvent event) {
        Map<Long, Session> m = ROOM_SESSIONS.get(roomId);
        if (m == null || event == null) return;
        Session s = m.get(userId);
        if (s != null) send(s, event.toJson());
    }

    /** 房间在线连接数 */
    public static int onlineCount(String roomId) {
        Map<Long, Session> m = ROOM_SESSIONS.get(roomId);
        return m == null ? 0 : m.size();
    }

    /** 关闭房间内所有连接（房间结算/关闭时调用） */
    public static void closeRoom(String roomId) {
        Map<Long, Session> m = ROOM_SESSIONS.remove(roomId);
        if (m == null) return;
        for (Session s : m.values()) closeQuietly(s, "房间已结束");
    }

    // ==================== 事件推送快捷方法 ====================

    /** 玩家加入（原型 30 等待开局人数变化） */
    public static void pushPlayerJoin(String roomId, String roomNo, Long roomIdLong, Map<String, Object> player) {
        broadcast(roomId, WsEvent.of(WsEvent.PLAYER_JOIN, roomIdLong, roomNo, player));
    }

    /** 玩家离开 */
    public static void pushPlayerLeave(String roomId, String roomNo, Long roomIdLong, Long userId, String nickname) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nickname", nickname);
        broadcast(roomId, WsEvent.of(WsEvent.PLAYER_LEAVE, roomIdLong, roomNo, data));
    }

    /** 准备状态变化（原型 30「已准备」绿勾） */
    public static void pushReady(String roomId, String roomNo, Long roomIdLong,
                                 Long userId, String nickname, boolean ready,
                                 int readyCount, int totalCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nickname", nickname);
        data.put("ready", ready);
        data.put("readyCount", readyCount);
        data.put("totalCount", totalCount);
        broadcast(roomId, WsEvent.of(WsEvent.PLAYER_READY, roomIdLong, roomNo, data));
    }

    /** 倒计时（原型 30 开局 / 32 抢庄 / 33 下注 / 35 下一局） */
    public static void pushCountdown(String roomId, String roomNo, Long roomIdLong, String purpose, int seconds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purpose", purpose);
        data.put("seconds", seconds);
        broadcast(roomId, WsEvent.of(WsEvent.COUNTDOWN, roomIdLong, roomNo, data));
    }

    /** 发牌（原型 31 发牌中） */
    public static void pushDeal(String roomId, String roomNo, Long roomIdLong, int round, Object dealData) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", round);
        data.put("deal", dealData);
        broadcast(roomId, WsEvent.of(WsEvent.DEAL, roomIdLong, roomNo, data));
    }

    /** 抢庄（原型 32 抢庄中） */
    public static void pushGrabBanker(String roomId, String roomNo, Long roomIdLong,
                                      Long bankerId, Integer bankerMultiplier, int seconds, Object grabStates) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bankerId", bankerId);
        data.put("bankerMultiplier", bankerMultiplier);
        data.put("countdown", seconds);
        data.put("grabStates", grabStates);
        broadcast(roomId, WsEvent.of(WsEvent.GRAB_BANKER, roomIdLong, roomNo, data));
    }

    /** 下注（原型 33 下注中） */
    public static void pushBet(String roomId, String roomNo, Long roomIdLong,
                               Object bets, Long pot, int seconds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bets", bets);
        data.put("pot", pot);
        data.put("countdown", seconds);
        broadcast(roomId, WsEvent.of(WsEvent.BET, roomIdLong, roomNo, data));
    }

    /** 比牌（原型 34 比牌中） */
    public static void pushCompare(String roomId, String roomNo, Long roomIdLong,
                                   Object winner, Object loser, String winnerType,
                                   Integer winnerMultiplier, Long pot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("winner", winner);
        data.put("loser", loser);
        data.put("winnerCardType", winnerType);
        data.put("winnerMultiplier", winnerMultiplier);
        data.put("pot", pot);
        broadcast(roomId, WsEvent.of(WsEvent.COMPARE, roomIdLong, roomNo, data));
    }

    /** 本局结算（原型 35 本局结算） */
    public static void pushRoundSettle(String roomId, String roomNo, Long roomIdLong,
                                       int round, Object profits, Long pot, int nextCountdown) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", round);
        data.put("profits", profits);
        data.put("pot", pot);
        data.put("nextRoundCountdown", nextCountdown);
        broadcast(roomId, WsEvent.of(WsEvent.ROUND_SETTLE, roomIdLong, roomNo, data));
    }

    /** 房间总局结算（原型 10 房间结算） */
    public static void pushRoomSettle(String roomId, String roomNo, Long roomIdLong, Object settleResult) {
        broadcast(roomId, WsEvent.of(WsEvent.ROOM_SETTLE, roomIdLong, roomNo, settleResult));
    }

    /** 赠送游戏币通知（原型 26/27） */
    public static void pushGift(String roomId, String roomNo, Long roomIdLong,
                                Long fromUserId, String fromName, Long toUserId,
                                String toName, Long amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fromUserId", fromUserId);
        data.put("fromName", fromName);
        data.put("toUserId", toUserId);
        data.put("toName", toName);
        data.put("amount", amount);
        broadcast(roomId, WsEvent.of(WsEvent.GIFT, roomIdLong, roomNo, data));
    }

    /** 错误下发 */
    public static void pushError(String roomId, String roomNo, Long roomIdLong, String message) {
        broadcast(roomId, WsEvent.of(WsEvent.ERROR, roomIdLong, roomNo, "message", message));
    }

    // ==================== 内部工具 ====================

    /** 构建牌桌状态快照（原型 36 断线重连恢复牌桌） */
    private WsEvent buildTableState(String roomId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            Long rid = Long.valueOf(roomId);
            if (roomServiceStatic != null) {
                com.poker.platform.entity.GameRoom room = roomServiceStatic.getRoomDetail(rid);
                List<RoomPlayer> players = roomServiceStatic.listRoomPlayers(rid);
                data.put("room", room);
                data.put("players", players);
                data.put("onlineCount", onlineCount(roomId));
                return WsEvent.of(WsEvent.TABLE_STATE, rid,
                        room == null ? null : room.getRoomNo(), data);
            }
        } catch (Exception e) {
            log.warn("构建牌桌快照失败 room={}: {}", roomId, e.getMessage());
        }
        data.put("reconnectFailed", true);
        return WsEvent.of(WsEvent.TABLE_STATE, null, roomId, data);
    }

    private String nicknameOf(Long uid) {
        try {
            if (userMapperStatic != null) {
                User u = userMapperStatic.selectById(uid);
                if (u != null) return u.getNickname() == null ? u.getUsername() : u.getNickname();
            }
        } catch (Exception ignore) { }
        return "玩家" + uid;
    }

    private String queryParam(Session session, String key) {
        String qs = session.getQueryString();
        if (qs == null) return null;
        for (String kv : qs.split("&")) {
            String[] arr = kv.split("=", 2);
            if (arr.length == 2 && key.equals(arr[0])) return arr[1];
        }
        return null;
    }

    private static void send(Session s, String msg) {
        if (s != null && s.isOpen()) {
            try {
                s.getBasicRemote().sendText(msg);
            } catch (IOException e) {
                log.warn("WS发送失败: {}", e.getMessage());
            }
        }
    }

    private static void closeQuietly(Session s, String reason) {
        try {
            s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception ignore) { }
    }
}
