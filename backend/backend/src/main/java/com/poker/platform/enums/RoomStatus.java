package com.poker.platform.enums;

/**
 * 房间状态枚举
 */
public enum RoomStatus {
    WAITING(0, "等待中"),
    PLAYING(1, "对局中"),
    SETTLED(2, "已结算"),
    CLOSED(3, "已关闭");

    private final Integer code;
    private final String desc;

    RoomStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
