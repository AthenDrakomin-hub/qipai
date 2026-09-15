package com.poker.platform.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 游戏币变动类型（原型 19-资金流水页 Tab 映射）
 *
 * 数据库 change_type 取值：
 *  1-注册赠送 2-代理增减下线 3-对局输赢 4-平台抽水 5-代理水费扣费
 *  6-总代理分润 7-客服人工调整 8-管理员调整 9-代理赠送玩家 10-补扣欠费
 *  11-系统返佣 12-玩家之间赠送(P2P) 13-推广划拨 14-对局赠送
 *
 * 原型 19 Tab：全部 / 充值 / 提现 / 佣金 / 赠送 / 对局
 */
public class CreditChangeType {

    private static final Map<Integer, String> DESC = new HashMap<>();
    /** change_type -> 所属 Tab code */
    private static final Map<Integer, String> TAB = new HashMap<>();

    static {
        DESC.put(1, "注册赠送");
        DESC.put(2, "代理增减下线");
        DESC.put(3, "对局输赢");
        DESC.put(4, "平台抽水");
        DESC.put(5, "代理水费扣费");
        DESC.put(6, "总代理分润");
        DESC.put(7, "客服人工调整");
        DESC.put(8, "管理员调整");
        DESC.put(9, "代理赠送玩家");
        DESC.put(10, "补扣欠费");
        DESC.put(11, "系统返佣");
        DESC.put(12, "玩家之间赠送");
        DESC.put(13, "推广划拨");
        DESC.put(14, "对局赠送");

        // 充值 Tab：注册赠送、客服/管理员调整、推广划拨（入账类）
        TAB.put(1, "RECHARGE");
        TAB.put(7, "RECHARGE");
        TAB.put(8, "RECHARGE");
        TAB.put(13, "RECHARGE");
        // 提现 Tab：平台抽水、水费扣费、补扣欠费（扣除类）
        TAB.put(4, "WITHDRAW");
        TAB.put(5, "WITHDRAW");
        TAB.put(10, "WITHDRAW");
        // 佣金 Tab：总代分润、系统返佣
        TAB.put(6, "COMMISSION");
        TAB.put(11, "COMMISSION");
        // 赠送 Tab：代理赠送、玩家赠送、对局赠送
        TAB.put(9, "GIFT");
        TAB.put(12, "GIFT");
        TAB.put(14, "GIFT");
        // 对局 Tab：代理增减下线、对局输赢
        TAB.put(2, "GAME");
        TAB.put(3, "GAME");
    }

    public static String descOf(Integer type) {
        if (type == null) return "";
        String d = DESC.get(type);
        return d == null ? ("其他(" + type + ")") : d;
    }

    /** change_type -> 原型 Tab code */
    public static String tabOf(Integer type) {
        if (type == null) return "ALL";
        String t = TAB.get(type);
        return t == null ? "ALL" : t;
    }

    /**
     * Tab code → change_type 集合。
     * 返回 null 表示「全部」（不筛选）；返回空集合表示该 Tab 无匹配类型。
     */
    public static Set<Integer> tabTypes(String tab) {
        if (tab == null || tab.trim().isEmpty() || "ALL".equalsIgnoreCase(tab.trim())) return null;
        String t = tab.trim().toUpperCase();
        switch (t) {
            case "充值": t = "RECHARGE"; break;
            case "提现": t = "WITHDRAW"; break;
            case "佣金": t = "COMMISSION"; break;
            case "赠送": t = "GIFT"; break;
            case "对局": t = "GAME"; break;
            default: break;
        }
        if (!"RECHARGE".equals(t) && !"WITHDRAW".equals(t) && !"COMMISSION".equals(t)
                && !"GIFT".equals(t) && !"GAME".equals(t)) {
            return null;
        }
        Set<Integer> set = new HashSet<>();
        for (Map.Entry<Integer, String> e : TAB.entrySet()) {
            if (e.getValue().equals(t)) set.add(e.getKey());
        }
        return set;
    }
}
