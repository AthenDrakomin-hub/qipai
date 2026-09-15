package com.poker.platform.game;

import com.poker.platform.enums.GameType;
import com.poker.platform.enums.RoomLevel;

/**
 * 六大游戏下注参数配置（按 游戏类型 + 房间等级）
 *
 * 抢庄牛/抢庄三公/德州：baseBet 底注, raiseBet 加注, cap 封顶
 * 通比牛/通比三公：fixedMin 固定下注下限, fixedMax 固定下注上限（房主在区间内设定当局固定筹码）
 * 金花：baseBet 底注, blindBet 闷牌下注, lookBet 看牌下注
 */
public class GameBetConfig {

    public static class BetParam {
        public final long baseBet;     // 底注 / 起下注
        public final long raiseBet;    // 加注
        public final long cap;         // 封顶（单局单玩家最大输赢上限）
        public final long fixedMin;    // 通比类固定下注下限
        public final long fixedMax;    // 通比类固定下注上限
        public final long blindBet;    // 金花闷牌下注
        public final long lookBet;     // 金花看牌下注

        public BetParam(long baseBet, long raiseBet, long cap, long fixedMin, long fixedMax, long blindBet, long lookBet) {
            this.baseBet = baseBet;
            this.raiseBet = raiseBet;
            this.cap = cap;
            this.fixedMin = fixedMin;
            this.fixedMax = fixedMax;
            this.blindBet = blindBet;
            this.lookBet = lookBet;
        }
    }

    /**
     * 获取下注参数。
     * 抢庄牛: 初级5-10-50 / 高级20-50-200 / 顶级50-100-500
     * 通比牛: 初级5-50 / 高级20-200 / 顶级50-500
     * 抢庄三公: 初级5-10-30 / 高级20-50-200 / 顶级50-100-500
     * 通比三公: 初级5-50 / 高级20-200 / 顶级50-500
     * 金花: 初级5底5闷10看 / 高级10底20闷40看 / 顶级50底50闷100看
     * 德州: 初级5-10-50 / 高级10-50-100 / 顶级50-100-200
     */
    public static BetParam get(GameType type, RoomLevel level) {
        if (type == null || level == null) {
            return new BetParam(10, 20, 100, 5, 50, 5, 10);
        }
        switch (type) {
            case DOUNIU: {
                // 抢庄牛：baseBet-raiseBet-cap
                switch (level) {
                    case PRIMARY:  return new BetParam(5, 10, 50, 0, 0, 0, 0);
                    case ADVANCED: return new BetParam(20, 50, 200, 0, 0, 0, 0);
                    case PREMIUM:  return new BetParam(50, 100, 500, 0, 0, 0, 0);
                }
            }
            case TONGBI_NIUNIU: {
                // 通比牛：固定金额 fixedMin-fixedMax
                switch (level) {
                    case PRIMARY:  return new BetParam(0, 0, 50, 5, 50, 0, 0);
                    case ADVANCED: return new BetParam(0, 0, 200, 20, 200, 0, 0);
                    case PREMIUM:  return new BetParam(0, 0, 500, 50, 500, 0, 0);
                }
            }
            case SANGONG: {
                // 抢庄三公：baseBet-raiseBet-cap
                switch (level) {
                    case PRIMARY:  return new BetParam(5, 10, 30, 0, 0, 0, 0);
                    case ADVANCED: return new BetParam(20, 50, 200, 0, 0, 0, 0);
                    case PREMIUM:  return new BetParam(50, 100, 500, 0, 0, 0, 0);
                }
            }
            case TONGBI_SANGONG: {
                // 通比三公：固定金额 fixedMin-fixedMax
                switch (level) {
                    case PRIMARY:  return new BetParam(0, 0, 50, 5, 50, 0, 0);
                    case ADVANCED: return new BetParam(0, 0, 200, 20, 200, 0, 0);
                    case PREMIUM:  return new BetParam(0, 0, 500, 50, 500, 0, 0);
                }
            }
            case JINHUA: {
                // 金花：baseBet底 / blindBet闷 / lookBet看
                switch (level) {
                    case PRIMARY:  return new BetParam(5, 0, 0, 0, 0, 5, 10);
                    case ADVANCED: return new BetParam(10, 0, 0, 0, 0, 20, 40);
                    case PREMIUM:  return new BetParam(50, 0, 0, 0, 0, 50, 100);
                }
            }
            case TEXAS: {
                // 德州：baseBet起下注 / raiseBet加注 / cap封顶
                switch (level) {
                    case PRIMARY:  return new BetParam(5, 10, 50, 0, 0, 0, 0);
                    case ADVANCED: return new BetParam(10, 50, 100, 0, 0, 0, 0);
                    case PREMIUM:  return new BetParam(50, 100, 200, 0, 0, 0, 0);
                }
            }
            default:
                return new BetParam(10, 20, 100, 5, 50, 5, 10);
        }
    }

    /** 通比类游戏：校验房主设定的固定下注额是否在区间内，越界则取边界 */
    public static long clampFixedBet(GameType type, RoomLevel level, long fixed) {
        BetParam p = get(type, level);
        if (fixed < p.fixedMin) return p.fixedMin;
        if (fixed > p.fixedMax) return p.fixedMax;
        return fixed;
    }
}
