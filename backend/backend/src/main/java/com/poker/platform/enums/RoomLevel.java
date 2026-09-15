package com.poker.platform.enums;

/**
 * 房间等级枚举
 */
public enum RoomLevel {
    PRIMARY("PRIMARY", "初级房", 200, 1000, 100),
    ADVANCED("ADVANCED", "高级房", 500, 5000, 1000),
    PREMIUM("PREMIUM", "顶级房", 1000, 20000, 3000);

    private final String code;
    private final String desc;
    private final Integer minBuyin;
    private final Integer maxBuyin;
    private final Integer creditThreshold;

    RoomLevel(String code, String desc, Integer minBuyin, Integer maxBuyin, Integer creditThreshold) {
        this.code = code;
        this.desc = desc;
        this.minBuyin = minBuyin;
        this.maxBuyin = maxBuyin;
        this.creditThreshold = creditThreshold;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public Integer getMinBuyin() { return minBuyin; }
    public Integer getMaxBuyin() { return maxBuyin; }
    public Integer getCreditThreshold() { return creditThreshold; }

    public static RoomLevel fromCode(String code) {
        for (RoomLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) return level;
        }
        return null;
    }
}
