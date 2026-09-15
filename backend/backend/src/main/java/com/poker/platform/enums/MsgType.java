package com.poker.platform.enums;

/**
 * 消息类型枚举（原型 20-消息通知页 Tab：全部/系统/佣金/投诉）
 */
public enum MsgType {

    SYSTEM(1, "系统"),
    COMMISSION(2, "佣金"),
    COMPLAINT(3, "投诉"),
    RECHARGE(4, "充值"),
    ACTIVITY(5, "活动");

    private final Integer code;
    private final String desc;

    MsgType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }

    public static MsgType fromCode(Integer code) {
        if (code == null) return null;
        for (MsgType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }

    /**
     * 将前端 Tab 名称映射为消息类型 code。
     * 原型 Tab：全部 / 系统 / 佣金 / 投诉
     * 支持中英文与大小写；无法识别或「全部」时返回 null（表示不筛选）。
     */
    public static Integer fromTab(String tab) {
        if (tab == null || tab.isEmpty()) return null;
        String t = tab.trim().toUpperCase();
        if ("ALL".equals(t) || "全部".equals(tab.trim())) return null;
        for (MsgType m : values()) {
            if (m.name().equals(t) || m.desc.equals(tab.trim())) return m.code;
        }
        return null;
    }
}
