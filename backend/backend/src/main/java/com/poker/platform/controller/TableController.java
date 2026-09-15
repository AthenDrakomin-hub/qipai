package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.dto.R;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.enums.GameType;
import com.poker.platform.enums.RoomStatus;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RoomPlayerMapper;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.GamePlayService;
import com.poker.platform.service.RoomService;
import com.poker.platform.websocket.GameWebSocket;
import com.poker.platform.websocket.WsEvent;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 牌桌对局阶段（原型 30-等待开局 到 36-断线重连）
 *
 * 覆盖原型页：
 *  30-等待开局    GET /table/{roomId}/state   POST /table/{roomId}/ready
 *  31-发牌中      POST /table/{roomId}/deal
 *  32-抢庄中      POST /table/{roomId}/grab-banker
 *  33-下注中      POST /table/{roomId}/bet
 *  34-比牌中      POST /table/{roomId}/compare
 *  35-本局结算    POST /table/{roomId}/play-round（复用）
 *  36-断线重连    GET  /table/{roomId}/state
 */
@RestController
@RequestMapping("/table")
public class TableController {

    /** 抢庄倒计时秒数（原型 32「15秒」） */
    private static final int GRAB_COUNTDOWN = 15;
    /** 下注倒计时秒数（原型 33「20秒」） */
    private static final int BET_COUNTDOWN = 20;
    /** 自动开局倒计时秒数（原型 30「10秒后自动开局」） */
    private static final int AUTO_START_COUNTDOWN = 10;
    /** 下一局倒计时秒数（原型 35「5秒后开始下一局」） */
    private static final int NEXT_ROUND_COUNTDOWN = 5;
    /** 开局最少人数 */
    private static final int MIN_PLAYERS = 2;

    @Resource private RoomService roomService;
    @Resource private GamePlayService gamePlayService;
    @Resource private RoomPlayerMapper roomPlayerMapper;
    @Resource private GameRoomMapper gameRoomMapper;

    /**
     * 牌桌状态（原型 30 等待开局 / 原型 36 断线重连）
     *
     * 返回：房间信息、玩家列表（含座位/筹码/准备状态）、阶段标记、倒计时建议值
     */
    @GetMapping("/{roomId}/state")
    public R<Map<String, Object>> state(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        GameRoom room = roomService.getRoomDetail(roomId);
        if (room == null) throw new BizException("房间不存在");
        List<RoomPlayer> players = roomService.listRoomPlayers(roomId);

        List<RoomPlayer> seated = new ArrayList<>();
        int readyCount = 0;
        RoomPlayer me = null;
        for (RoomPlayer p : players) {
            if (Boolean.TRUE.equals(p.getIsObserver())) continue;
            seated.add(p);
            if (Boolean.TRUE.equals(p.getIsReady())) readyCount++;
            if (p.getUserId().equals(uid)) me = p;
        }

        Map<String, Object> stage = new LinkedHashMap<>();
        // 阶段判定：等待开局 / 对局中
        boolean waiting = RoomStatus.WAITING.getCode().equals(room.getStatus());
        stage.put("waiting", waiting);
        stage.put("playing", RoomStatus.PLAYING.getCode().equals(room.getStatus()));
        stage.put("settled", RoomStatus.SETTLED.getCode().equals(room.getStatus()));
        stage.put("closed", RoomStatus.CLOSED.getCode().equals(room.getStatus()));
        stage.put("needPlayers", Math.max(0, MIN_PLAYERS - seated.size()));
        stage.put("autoStartCountdown", waiting ? AUTO_START_COUNTDOWN : 0);
        stage.put("grabCountdown", GRAB_COUNTDOWN);
        stage.put("betCountdown", BET_COUNTDOWN);
        stage.put("nextRoundCountdown", NEXT_ROUND_COUNTDOWN);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("room", room);
        res.put("players", seated);
        res.put("observers", players.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsObserver()))
                .collect(java.util.stream.Collectors.toList()));
        res.put("me", me);
        res.put("isObserver", me == null || Boolean.TRUE.equals(me.getIsObserver()));
        res.put("readyCount", readyCount);
        res.put("totalCount", seated.size());
        res.put("minPlayers", MIN_PLAYERS);
        res.put("stage", stage);
        res.put("onlineCount", GameWebSocket.onlineCount(String.valueOf(roomId)));
        // 无庄标记（原型 13/14/15/16 通比玩法显示「通比」）
        GameType type = GameType.fromCode(room.getGameType());
        res.put("noBanker", type == GameType.TONGBI_NIUNIU || type == GameType.TONGBI_SANGONG);
        res.put("fixedBetAmount", room.getFixedBetAmount());
        return R.ok(res);
    }

    /**
     * 玩家准备（原型 30「准备」按钮 → 头像右上绿勾「已准备」）
     *
     * 服务端行为：切换当前用户准备状态；若已准备人数 >= 2 且全员准备，推送开局倒计时。
     */
    @PostMapping("/{roomId}/ready")
    public R<Map<String, Object>> ready(@PathVariable Long roomId,
                                        @RequestParam(required = false) Boolean ready) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (RoomStatus.CLOSED.getCode().equals(room.getStatus())) throw new BizException("房间已关闭");
        if (RoomStatus.SETTLED.getCode().equals(room.getStatus())) throw new BizException("房间已结算");

        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法准备");

        boolean target = ready == null ? !Boolean.TRUE.equals(me.getIsReady()) : ready;
        me.setIsReady(target);
        roomPlayerMapper.updateById(me);

        List<RoomPlayer> seated = seatedPlayers(roomId);
        int readyCount = 0;
        for (RoomPlayer p : seated) if (Boolean.TRUE.equals(p.getIsReady())) readyCount++;

        GameWebSocket.pushReady(String.valueOf(roomId), room.getRoomNo(), roomId,
                uid, me.getNickname(), target, readyCount, seated.size());

        // 人数达标且全员准备 → 推送开局倒计时（原型 30「10秒后自动开局」）
        boolean allReady = seated.size() >= MIN_PLAYERS && readyCount == seated.size();
        if (allReady) {
            GameWebSocket.pushCountdown(String.valueOf(roomId), room.getRoomNo(), roomId,
                    WsEvent.CD_START, AUTO_START_COUNTDOWN);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ready", target);
        res.put("readyCount", readyCount);
        res.put("totalCount", seated.size());
        res.put("allReady", allReady);
        res.put("autoStartCountdown", allReady ? AUTO_START_COUNTDOWN : 0);
        return R.ok(target ? "已准备" : "已取消准备", res);
    }

    /**
     * 抢庄（原型 32-抢庄中）
     *
     * 页面元素：抢庄/不抢 按钮、倍率选择 1倍/2倍/3倍/4倍、15 秒倒计时环、
     *          其他玩家头像旁的「抢庄2倍」「不抢」标记
     *
     * @param grab       是否抢庄
     * @param multiplier 抢庄倍数（1-4，grab=false 时忽略）
     */
    @PostMapping("/{roomId}/grab-banker")
    public R<Map<String, Object>> grabBanker(@PathVariable Long roomId,
                                             @RequestParam Boolean grab,
                                             @RequestParam(required = false) Integer multiplier) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (!isGrabGame(room.getGameType())) {
            throw new BizException("当前玩法不支持抢庄（仅抢庄三公/抢庄牛牛）");
        }
        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法抢庄");

        int mult = 1;
        if (Boolean.TRUE.equals(grab)) {
            if (multiplier == null || multiplier < 1 || multiplier > 4) {
                throw new BizException("抢庄倍数需为 1-4 倍");
            }
            mult = multiplier;
        }

        GameWebSocket.pushGrabBanker(String.valueOf(roomId), room.getRoomNo(), roomId,
                null, Boolean.TRUE.equals(grab) ? mult : null, GRAB_COUNTDOWN,
                grabStatePayload(roomId, uid, grab, mult));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("grab", grab);
        res.put("multiplier", Boolean.TRUE.equals(grab) ? mult : 0);
        res.put("grabStates", grabStatePayload(roomId, uid, grab, mult));
        res.put("countdown", GRAB_COUNTDOWN);
        return R.ok(Boolean.TRUE.equals(grab) ? ("已抢庄 " + mult + "倍") : "已选择不抢", res);
    }

    /**
     * 下注（原型 33-下注中）
     *
     * 页面元素：底池、各座位「下注 500」标签、20 秒倒计时环、
     *          筹码按钮 100/200/500/1000、「弃牌」「下注」、「定义金额」下拉
     *
     * @param amount 下注金额；为 0 表示弃牌
     */
    @PostMapping("/{roomId}/bet")
    public R<Map<String, Object>> bet(@PathVariable Long roomId,
                                      @RequestParam Long amount,
                                      @RequestParam(required = false) Boolean fold) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (RoomStatus.CLOSED.getCode().equals(room.getStatus())) throw new BizException("房间已关闭");

        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法下注");

        boolean folded = Boolean.TRUE.equals(fold) || amount == null || amount <= 0;
        long amt = folded ? 0L : amount;

        if (!folded) {
            long cur = me.getCurrentCredits() == null ? 0L : me.getCurrentCredits();
            if (cur < amt) throw new BizException("筹码不足，当前 " + cur + "，本局下注 " + amt);
            Integer min = room.getMinBuyin();
            Integer max = room.getMaxBuyin();
            if (min != null && amt < min.longValue()) throw new BizException("单注不能低于 " + min);
            if (max != null && amt > max.longValue() * 10L) throw new BizException("单注不能高于 " + (max.longValue() * 10L));
        }

        long pot = room.getTotalTurnover() == null ? 0L : room.getTotalTurnover();

        Map<String, Object> bets = new LinkedHashMap<>();
        bets.put(String.valueOf(uid), amt);
        bets.put("folded", folded);

        GameWebSocket.pushBet(String.valueOf(roomId), room.getRoomNo(), roomId,
                bets, pot + amt, BET_COUNTDOWN);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userId", uid);
        res.put("amount", amt);
        res.put("folded", folded);
        res.put("pot", pot + amt);
        res.put("countdown", BET_COUNTDOWN);
        return R.ok(folded ? "已弃牌" : "下注成功", res);
    }

    /**
     * 比牌（原型 34-比牌中）
     *
     * 页面元素：VS 对比、双方牌面、牌型标签（顺子/三条）、倍率 2倍/4倍、
     *          「小王 胜!」、「小王获胜，赢得底池 12,500」
     *
     * 实现说明：以服务端已结算的最近一局结果为比牌依据（防作弊），
     *          本接口仅负责触发一次服务端对局并推送比牌事件。
     */
    @PostMapping("/{roomId}/compare")
    public R<Map<String, Object>> compare(@PathVariable Long roomId,
                                          @RequestParam(required = false) Long targetUserId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        boolean jinhua = GameType.JINHUA.getCode().equals(room.getGameType());
        if (!jinhua) {
            throw new BizException("比牌动作仅炸金花玩法支持");
        }
        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法比牌");

        if (targetUserId == null && body != null && body.get("targetUserId") != null) {
            targetUserId = Long.valueOf(String.valueOf(body.get("targetUserId")));
        }
        if (targetUserId == null) throw new BizException("请选择比牌对象");
        if (targetUserId.equals(uid)) throw new BizException("不能与自己比牌");
        if (seatOf(roomId, targetUserId) == null) throw new BizException("比牌对象不在此房间座位上");

        Map<Long, Long> bets = new LinkedHashMap<>();
        Map<String, Object> roundResult = gamePlayService.playOneRound(roomId, bets, null);

        Object winner = roundResult.get("bankerId");
        Object cardTypes = roundResult.get("cards");
        @SuppressWarnings("unchecked")
        Map<String, Object> profits = (Map<String, Object>) roundResult.get("profits");
        String winnerName = null;
        Long pot = roundResult.get("roundTurnover") == null ? 0L
                : Long.valueOf(String.valueOf(roundResult.get("roundTurnover")));

        // 从本局盈亏中挑出赢家作为比牌胜方（原型 34「小王 胜!」）
        if (profits != null) {
            long best = Long.MIN_VALUE;
            for (Map.Entry<String, Object> e : profits.entrySet()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) e.getValue();
                    Object np = p == null ? null : p.get("netProfit");
                    if (np != null && Long.parseLong(String.valueOf(np)) > best) {
                        best = Long.parseLong(String.valueOf(np));
                        winner = p.get("userId");
                        winnerName = (String) p.get("nickname");
                    }
                } catch (Exception ignore) { }
            }
        }

        String winnerType = null;
        Integer winnerMult = null;
        if (cardTypes instanceof Map) {
            Object types = ((Map<?, ?>) cardTypes).get("cardTypes");
            if (types instanceof Map && winner != null) {
                Object ti = ((Map<?, ?>) types).get(String.valueOf(winner));
                if (ti instanceof Map) {
                    winnerType = (String) ((Map<?, ?>) ti).get("name");
                    Object mv = ((Map<?, ?>) ti).get("multiplier");
                    winnerMult = mv == null ? null : Integer.valueOf(String.valueOf(mv));
                }
            }
        }

        Object loser = targetUserId;
        String targetName = null;
        RoomPlayer tp = seatOf(roomId, targetUserId);
        if (tp != null) targetName = tp.getNickname();

        GameWebSocket.pushCompare(String.valueOf(roomId), room.getRoomNo(), roomId,
                winner, loser, winnerType, winnerMult, pot);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("roundResult", roundResult);
        res.put("winner", winner);
        res.put("winnerName", winnerName);
        res.put("loser", loser);
        res.put("loserName", targetName);
        res.put("winnerCardType", winnerType);
        res.put("winnerMultiplier", winnerMult);
        res.put("pot", pot);
        return R.ok(winnerName == null ? "比牌完成" : (winnerName + " 胜!"), res);
    }

    /**
     * 发牌（原型 31-发牌中）
     * 触发一次服务端发牌并推送 DEAL 事件，供前端播发牌动画。
     */
    @PostMapping("/{roomId}/deal")
    public R<Map<String, Object>> deal(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (seatOf(roomId, uid) == null) throw new BizException("您不在此房间座位上");

        int round = (room.getCurrentRound() == null ? 0 : room.getCurrentRound()) + 1;
        GameWebSocket.pushDeal(String.valueOf(roomId), room.getRoomNo(), roomId, round, null);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("round", round);
        res.put("totalRounds", room.getTotalRounds());
        res.put("gameType", room.getGameType());
        return R.ok("发牌中", res);
    }

    /**
     * 看牌（原型 12-炸金花牌桌：闷牌 → 看牌）
     * 与旧接口 /room/{roomId}/look 等价，此处以牌桌语义重新暴露并推送状态。
     */
    @PostMapping("/{roomId}/look")
    public R<Map<String, Object>> look(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (!GameType.JINHUA.getCode().equals(room.getGameType())) {
            throw new BizException("仅炸金花玩法支持看牌/闷牌");
        }
        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法看牌");

        me.setIsLooked(true);
        roomPlayerMapper.updateById(me);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("isLooked", true);
        res.put("lookBet", "看牌后下注按闷注的2倍执行");
        return R.ok("已看牌", res);
    }

    /**
     * 自动挂机开关（原型 23 设置页 / 原型 45 牌桌设置面板）
     * 通比类玩法开启后每局自动下注参与。
     */
    @PostMapping("/{roomId}/auto-play")
    public R<Map<String, Object>> autoPlay(@PathVariable Long roomId,
                                           @RequestParam(required = false) Boolean enabled) {
        Long uid = UserContext.getUserId();
        RoomPlayer me = seatOf(roomId, uid);
        if (me == null) throw new BizException("您不在此房间座位上，无法切换自动挂机");
        boolean target = enabled == null ? !Boolean.TRUE.equals(me.getIsAutoPlay()) : enabled;
        me.setIsAutoPlay(target);
        roomPlayerMapper.updateById(me);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("isAutoPlay", target);
        return R.ok(target ? "已开启自动挂机" : "已关闭自动挂机", res);
    }

    /**
     * 退出房间（原型 37-通用弹窗「确定要退出房间吗?」→「确定退出」）
     * 退出后本局按弃牌处理，剩余筹码退回账户。
     */
    @PostMapping("/{roomId}/quit")
    public R<Void> quit(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");

        RoomPlayer me = seatOf(roomId, uid);
        String nickname = me == null ? null : me.getNickname();

        roomService.leaveRoom(uid, roomId);
        GameWebSocket.pushPlayerLeave(String.valueOf(roomId), room.getRoomNo(), roomId, uid, nickname);
        return R.ok("已退出房间，未完成对局按弃牌处理", null);
    }

    // ==================== 内部工具 ====================

    private RoomPlayer seatOf(Long roomId, Long uid) {
        return roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, uid)
                .and(w -> w.isNull(RoomPlayer::getIsObserver).or().eq(RoomPlayer::getIsObserver, false)));
    }

    private List<RoomPlayer> seatedPlayers(Long roomId) {
        List<RoomPlayer> all = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId));
        List<RoomPlayer> seated = new ArrayList<>();
        for (RoomPlayer p : all) {
            if (!Boolean.TRUE.equals(p.getIsObserver())) seated.add(p);
        }
        return seated;
    }

    /** 是否抢庄类玩法（原型 11 抢庄三公 / 12 抢庄牛牛） */
    private boolean isGrabGame(String gameType) {
        return GameType.SANGONG.getCode().equals(gameType) || GameType.DOUNIU.getCode().equals(gameType);
    }

    /** 构造各玩家抢庄状态（原型 32：抢庄2倍 / 抢庄3倍 / 不抢） */
    private List<Map<String, Object>> grabStatePayload(Long roomId, Long uid, Boolean grab, Integer mult) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RoomPlayer p : seatedPlayers(roomId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", p.getUserId());
            m.put("nickname", p.getNickname());
            if (p.getUserId().equals(uid)) {
                m.put("grabbed", Boolean.TRUE.equals(grab));
                m.put("multiplier", Boolean.TRUE.equals(grab) ? mult : 0);
                m.put("label", Boolean.TRUE.equals(grab) ? ("抢庄" + mult + "倍") : "不抢");
            } else {
                m.put("grabbed", null);
                m.put("multiplier", null);
                m.put("label", "等待中");
            }
            list.add(m);
        }
        return list;
    }
}
