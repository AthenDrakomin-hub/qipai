package com.poker.platform.websocket;

import com.alibaba.fastjson.JSON;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 牌桌实时事件协议（原型 30-36 各阶段动画驱动）
 *
 * 服务端 → 客户端事件类型：
 *   TABLE_STATE   牌桌状态快照（首次连接 / 断线重连恢复，原型 36）
 *   PLAYER_JOIN   玩家加入
 *   PLAYER_LEAVE  玩家离开
 *   PLAYER_READY  玩家准备状态变化（原型 30 等待开局：绿勾）
 *   COUNTDOWN     倒计时（原型 30 开局倒计时 / 32 抢庄 15 秒 / 33 下注 20 秒 / 35 下局 5 秒）
 *   DEAL          发牌（原型 31 发牌中）
 *   GRAB_BANKER   抢庄阶段与各玩家抢庄状态（原型 32 抢庄中）
 *   BET           下注阶段与各玩家下注额（原型 33 下注中）
 *   COMPARE       比牌结果（原型 34 比牌中）
 *   ROUND_SETTLE  本局结算与各玩家盈亏（原型 35 本局结算）
 *   ROOM_SETTLE   房间总局结算（原型 10 房间结算）
 *   CHAT          聊天消息（原型 43 聊天面板）
 *   EMOJI         表情消息（原型 44 表情面板）
 *   GIFT          赠送游戏币通知（原型 26/27）
 *   ERROR         错误
 *
 * 客户端 → 服务端指令：
 *   CHAT / EMOJI / PING
 */
public class WsEvent {

    // ===== 服务端事件类型 =====
    public static final String TABLE_STATE   = "TABLE_STATE";
    public static final String PLAYER_JOIN   = "PLAYER_JOIN";
    public static final String PLAYER_LEAVE  = "PLAYER_LEAVE";
    public static final String PLAYER_READY  = "PLAYER_READY";
    public static final String COUNTDOWN     = "COUNTDOWN";
    public static final String DEAL          = "DEAL";
    public static final String GRAB_BANKER   = "GRAB_BANKER";
    public static final String BET           = "BET";
    public static final String COMPARE       = "COMPARE";
    public static final String ROUND_SETTLE  = "ROUND_SETTLE";
    public static final String ROOM_SETTLE   = "ROOM_SETTLE";
    public static final String CHAT          = "CHAT";
    public static final String EMOJI         = "EMOJI";
    public static final String GIFT          = "GIFT";
    public static final String PONG          = "PONG";
    public static final String ERROR         = "ERROR";

    // ===== 客户端指令类型 =====
    public static final String CMD_CHAT  = "CHAT";
    public static final String CMD_EMOJI = "EMOJI";
    public static final String CMD_PING  = "PING";

    /** 倒计时用途：开局 / 抢庄 / 下注 / 比牌 / 下一局 */
    public static final String CD_START   = "START";
    public static final String CD_GRAB    = "GRAB_BANKER";
    public static final String CD_BET     = "BET";
    public static final String CD_COMPARE = "COMPARE";
    public static final String CD_NEXT    = "NEXT_ROUND";

    private String type;
    private Long roomId;
    private String roomNo;
    private Long ts;
    private Object data;

    public WsEvent() {
        this.ts = System.currentTimeMillis();
    }

    public WsEvent(String type, Long roomId, String roomNo, Object data) {
        this.type = type;
        this.roomId = roomId;
        this.roomNo = roomNo;
        this.data = data;
        this.ts = System.currentTimeMillis();
    }

    /** 构造事件（链式，便于快速拼装） */
    public static WsEvent of(String type, Long roomId, String roomNo, Object data) {
        return new WsEvent(type, roomId, roomNo, data);
    }

    /** 构造 data 为单键 Map 的事件，如 COUNTDOWN {purpose, seconds} */
    public static WsEvent of(String type, Long roomId, String roomNo, String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return new WsEvent(type, roomId, roomNo, m);
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getTs() { return ts; }
    public void setTs(Long ts) { this.ts = ts; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
