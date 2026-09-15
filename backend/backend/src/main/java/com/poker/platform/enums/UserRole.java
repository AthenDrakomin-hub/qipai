package com.poker.platform.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {
    PLAYER(1, "普通玩家"),
    AGENT(2, "二级代理"),
    GENERAL_AGENT(3, "总代理"),
    CUSTOMER_SERVICE(4, "客服后台"),
    SUPER_ADMIN(5, "超级管理"),
    PRIMARY_AGENT(6, "一级代理");

    private final Integer code;
    private final String desc;

    UserRole(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }

    public static UserRole fromCode(Integer code) {
        for (UserRole role : values()) {
            if (role.code.equals(code)) return role;
        }
        return null;
    }

    /** 是否代理角色（一级/二级/总代理均可开房） */
    public static boolean isAgent(Integer code) {
        return AGENT.getCode().equals(code)
                || PRIMARY_AGENT.getCode().equals(code)
                || GENERAL_AGENT.getCode().equals(code);
    }
}
