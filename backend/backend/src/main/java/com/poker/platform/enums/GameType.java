package com.poker.platform.enums;

/**
 * 游戏类型枚举
 */
public enum GameType {
    TEXAS("TEXAS", "德州竞技"),
    SANGONG("SANGONG", "三公竞技"),
    JINHUA("JINHUA", "金花竞技"),
    DOUNIU("DOUNIU", "斗牛竞技"),
    TONGBI_NIUNIU("TONGBI_NIUNIU", "通比牛牛"),
    TONGBI_SANGONG("TONGBI_SANGONG", "通比三公");

    private final String code;
    private final String desc;

    GameType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static GameType fromCode(String code) {
        for (GameType type : values()) {
            if (type.code.equalsIgnoreCase(code)) return type;
        }
        return null;
    }

    /** 按 code 取中文名，未知 code 返回原值，便于前端直接展示 */
    public static String descOf(String code) {
        GameType type = fromCode(code);
        return type == null ? code : type.getDesc();
    }
}
