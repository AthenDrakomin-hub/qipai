package com.poker.platform.game;

import java.util.*;

/**
 * 扑克基础工具：牌组、发牌、比牌核心工具
 */
public class PokerEngine {

    /**
     * 牌：1-52
     * rank: 2-14 (2..10 J Q K A)
     * suit: 0♠ 1♥ 2♣ 3♦
     */
    public static class Card {
        public final int id;
        public final int rank;   // 2..14
        public final int suit;   // 0..3
        public Card(int id) {
            this.id = id;
            this.rank = (id % 13) + 2;
            this.suit = id / 13;
        }
        public String rankName() {
            if (rank <= 10) return String.valueOf(rank);
            String[] names = {"","J","Q","K","A"};
            return names[rank - 10];
        }
        public String suitName() {
            return new String[]{"♠","♥","♣","♦"}[suit];
        }
        public boolean isRed() { return suit == 1 || suit == 3; }
        @Override public String toString() { return suitName() + rankName(); }
    }

    public static List<Card> newDeck() {
        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) deck.add(new Card(i));
        Collections.shuffle(deck, new Random());
        return deck;
    }

    // ============ 牌型评级（德州/金花通用，返回7位数用于比大小：类型*1e6 + 主牌*1e4 + 次牌*1e2.. ）============
    // 金花/梭哈用5张牌评级： 8-同花顺 7-四条 6-葫芦 5-同花 4-顺子 3-三条 2-两对 1-一对 0-高牌
    public static long rate5Cards(List<Card> cards) {
        int[] r = cards.stream().sorted(Comparator.comparingInt(c -> -c.rank))
                .mapToInt(c -> c.rank).toArray();
        boolean flush = cards.stream().allMatch(c -> c.suit == cards.get(0).suit);
        // 顺子：连续5张 (特殊: A-2-3-4-5)
        boolean straight = false;
        int straightHigh = 0;
        int[] sorted = Arrays.stream(r).sorted().toArray();
        if (sorted[4] - sorted[0] == 4 && sorted[0] != sorted[1] && sorted[1] != sorted[2]
                && sorted[2] != sorted[3] && sorted[3] != sorted[4]) {
            straight = true; straightHigh = sorted[4];
        } else if (Arrays.equals(sorted, new int[]{2,3,4,5,14})) {
            straight = true; straightHigh = 5;
        }
        // 计牌频率
        Map<Integer, Integer> cnt = new HashMap<>();
        for (int x : r) cnt.merge(x, 1, Integer::sum);
        List<Map.Entry<Integer,Integer>> grp = new ArrayList<>(cnt.entrySet());
        grp.sort((a,b) -> b.getValue().equals(a.getValue()) ? b.getKey()-a.getKey() : b.getValue()-a.getValue());
        int t1 = grp.get(0).getValue();
        int t2 = grp.size() > 1 ? grp.get(1).getValue() : 0;
        int r1 = grp.get(0).getKey();
        int r2 = grp.size() > 1 ? grp.get(1).getKey() : 0;
        int type;
        if (flush && straight) type = 8; // 同花顺
        else if (t1 == 4) type = 7; // 四条
        else if (t1 == 3 && t2 == 2) type = 6; // 葫芦
        else if (flush) type = 5;
        else if (straight) type = 4;
        else if (t1 == 3) type = 3;
        else if (t1 == 2 && t2 == 2) type = 2;
        else if (t1 == 2) type = 1;
        else type = 0;
        long score = (long) type * 1_000_000L;
        if (straight && straightHigh != r[0]) {
            score += (long) straightHigh * 10_000L;
        } else {
            score += (long) r1 * 10_000L + (long) r2 * 100L;
            // 剩余牌
            int idx = 1;
            for (int i = 2; i < grp.size() && idx <= 3; i++) {
                score += (long) grp.get(i).getKey() * (long) Math.pow(10, 4 - idx * 2);
                idx++;
            }
        }
        return score;
    }

    // ============ 斗牛 / 通比牛牛：5张牌，3张凑10倍数为有牛，牛值=后2张%10，牛值0为牛牛 ============
    public static int calcNiuValue(List<Card> cards) {
        if (cards.size() != 5) return -1;
        // 每张牌点数：J/Q/K = 10, A = 1, 2-10 = 本身
        int[] v = cards.stream().mapToInt(c -> {
            int r = c.rank;
            if (r >= 11 && r <= 13) return 10;
            if (r == 14) return 1;
            return r;
        }).toArray();
        // 枚举3张组合
        for (int i = 0; i < 5; i++) for (int j = i+1; j < 5; j++) for (int k = j+1; k < 5; k++) {
            if ((v[i] + v[j] + v[k]) % 10 == 0) {
                int sum = 0;
                for (int m = 0; m < 5; m++) if (m != i && m != j && m != k) sum += v[m];
                int niu = sum % 10;
                return niu == 0 ? 10 : niu; // 10 表示牛牛
            }
        }
        return 0; // 无牛
    }

    /** 斗牛比较：牛数大的赢，同牛数比最大单牌（点数A=1 < 2.. < K，花色♠>♥>♣>♦） */
    public static int compareNiu(List<Card> a, List<Card> b) {
        int na = calcNiuValue(a);
        int nb = calcNiuValue(b);
        if (na != nb) return na - nb;
        // 同牛，比最大单牌（A=14视为最大点数；斗牛中一般K最大，A最小，这里按K>A处理更通用）
        List<Card> as = new ArrayList<>(a); List<Card> bs = new ArrayList<>(b);
        as.sort((x,y) -> {
            int rx = x.rank == 14 ? 1 : x.rank;
            int ry = y.rank == 14 ? 1 : y.rank;
            if (rx != ry) return ry - rx;
            return x.suit - y.suit; // 花色：0♠ 大
        });
        bs.sort((x,y) -> {
            int rx = x.rank == 14 ? 1 : x.rank;
            int ry = y.rank == 14 ? 1 : y.rank;
            if (rx != ry) return ry - rx;
            return x.suit - y.suit;
        });
        Card ca = as.get(0), cb = bs.get(0);
        int ra = ca.rank == 14 ? 1 : ca.rank;
        int rb = cb.rank == 14 ? 1 : cb.rank;
        if (ra != rb) return ra - rb;
        return cb.suit - ca.suit;
    }

    // ============ 三公：3张牌 J/Q/K为公，公数多者赢，同公比点数（J/Q/K=10,A=1）============
    public static int calcSangongValue(List<Card> cards) {
        // 返回值 公数*100 + 点数（方便比较）
        int gong = 0; int sum = 0;
        for (Card c : cards) {
            if (c.rank >= 11 && c.rank <= 13) gong++;
            int v;
            if (c.rank >= 11 && c.rank <= 13) v = 10;
            else if (c.rank == 14) v = 1;
            else v = c.rank;
            sum += v;
        }
        return gong * 100 + (sum % 10);
    }

    public static int compareSangong(List<Card> a, List<Card> b) {
        return calcSangongValue(a) - calcSangongValue(b);
    }

    // ============ 骰子：1-6 ============
    public static int[] rollDice(int n) {
        int[] r = new int[n];
        Random rand = new Random();
        for (int i = 0; i < n; i++) r[i] = rand.nextInt(6) + 1;
        return r;
    }

    // ============ 抢庄牛倍数：牛牛4倍 / 牛9三倍 / 牛8两倍 / 牛7及以下(含没牛)1倍 ============
    // niuValue 来自 calcNiuValue：10=牛牛, 9=牛9, 8=牛8, 0=没牛, 1~7=牛1~牛7
    public static int niuMultiplier(int niuValue) {
        if (niuValue >= 10) return 4;  // 牛牛 ×4（封顶）
        if (niuValue == 9) return 3;   // 牛9 ×3
        if (niuValue == 8) return 2;   // 牛8 ×2
        return 1;                       // 牛7及以下 / 没牛 ×1（正常比大小）
    }

    public static String niuTypeName(int niuValue) {
        if (niuValue >= 10) return "牛牛";
        if (niuValue == 0) return "没牛";
        return "牛" + niuValue;
    }

    // ============ 抢庄三公牌型与倍数 ============
    // 大三公×6：三张都是公牌(J/Q/K)且点数完全相同
    // 小三公×5：三张都是公牌，恰好两张点数相同
    // 混三公×5：三张都是公牌且点数各不相同
    // 豹子×4：三张牌点数相同（含非公牌 AAA/222.../101010）
    // 双公9点×3：恰好两张公牌，第三张为9
    // 双公8点×2：恰好两张公牌，第三张为8
    // 9点×3：单公或无公，三张点数和取个位为9
    // 8点×2：单公或无公，点数和取个位为8
    // 7点及以下×1：单公或无公，点数0~7
    public static class SangongResult {
        public final String typeName;
        public final int multiplier;
        /** 牌型层级（越大越强），独立于倍数，避免"倍数相同"导致的跨档误判 */
        public final int tier;
        public final long compareValue; // tier*1e9 + maxRank*1e6 + sumMod*1e3 + 花色微调
        public SangongResult(String typeName, int multiplier, int tier, long compareValue) {
            this.typeName = typeName; this.multiplier = multiplier;
            this.tier = tier; this.compareValue = compareValue;
        }
    }

    /** 牌型层级常量（越大越强），与倍数解耦：
     *  大三公 9 > 小三公 8 > 混三公 7 > 豹子 6 > 双公9点 5 > 9点 5 > 双公8点 4 > 8点 4 > 7点及以下 1
     *  同层级内：maxRank 大者胜，再比点数个位 sumMod，最后比最大花色。 */
    private static final int TIER_DA_SANGONG   = 9;
    private static final int TIER_XIAO_SANGONG = 8;
    private static final int TIER_HUN_SANGONG  = 7;
    private static final int TIER_BAOZI        = 6;
    private static final int TIER_DG_9         = 5; // 双公9点，与9点同层
    private static final int TIER_NINE         = 5;
    private static final int TIER_DG_8         = 4; // 双公8点，与8点同层
    private static final int TIER_EIGHT        = 4;
    private static final int TIER_LOW          = 1;

    private static long sgCompare(int tier, int maxRank, int sumMod) {
        return (long) tier * 1_000_000_000L + (long) maxRank * 1_000_000L + (long) sumMod * 1_000L;
    }

    public static SangongResult calcSangongType(List<Card> cards) {
        if (cards == null || cards.size() < 3) {
            return new SangongResult("7点及以下", 1, TIER_LOW, sgCompare(TIER_LOW, 0, 0));
        }
        int gong = 0;
        int sum = 0;
        int[] ranks = new int[3];
        for (int i = 0; i < 3; i++) {
            Card c = cards.get(i);
            ranks[i] = c.rank;
            boolean isGong = (c.rank >= 11 && c.rank <= 13);
            if (isGong) gong++;
            int v;
            if (isGong) v = 10;
            else if (c.rank == 14) v = 1;
            else v = c.rank;
            sum += v;
        }
        int sumMod = sum % 10;
        // 最大单牌点数（A=1最小，2..K递增）
        int maxRank = 0;
        for (int r : ranks) {
            int rv = (r == 14) ? 1 : r;
            if (rv > maxRank) maxRank = rv;
        }
        int[] sorted = ranks.clone();
        Arrays.sort(sorted);
        boolean allSame = sorted[0] == sorted[2];
        boolean twoSame = sorted[0] == sorted[1] || sorted[1] == sorted[2];

        // 1. 大三公：3公且三张相同（JJJ/QQQ/KKK）
        if (gong == 3 && allSame) {
            return new SangongResult("大三公", 6, TIER_DA_SANGONG, sgCompare(TIER_DA_SANGONG, maxRank, sumMod));
        }
        // 2. 小三公：3公且恰好两张相同
        if (gong == 3 && twoSame) {
            return new SangongResult("小三公", 5, TIER_XIAO_SANGONG, sgCompare(TIER_XIAO_SANGONG, maxRank, sumMod));
        }
        // 3. 混三公：3公且各不相同
        if (gong == 3) {
            return new SangongResult("混三公", 5, TIER_HUN_SANGONG, sgCompare(TIER_HUN_SANGONG, maxRank, sumMod));
        }
        // 4. 豹子：三张点数相同（非公牌，AAA/222.../101010）
        if (allSame) {
            return new SangongResult("豹子", 4, TIER_BAOZI, sgCompare(TIER_BAOZI, maxRank, sumMod));
        }
        // 5/6. 双公9点/双公8点：恰好两张公牌，第三张为9或8
        if (gong == 2) {
            int thirdVal = 0;
            for (int r : ranks) {
                if (!(r >= 11 && r <= 13)) {
                    thirdVal = (r == 14) ? 1 : r;
                }
            }
            if (thirdVal == 9) return new SangongResult("双公9点", 3, TIER_DG_9, sgCompare(TIER_DG_9, maxRank, sumMod));
            if (thirdVal == 8) return new SangongResult("双公8点", 2, TIER_DG_8, sgCompare(TIER_DG_8, maxRank, sumMod));
            // 其他双公按点数和判定
        }
        // 7. 9点
        if (sumMod == 9) return new SangongResult("9点", 3, TIER_NINE, sgCompare(TIER_NINE, maxRank, sumMod));
        // 8. 8点
        if (sumMod == 8) return new SangongResult("8点", 2, TIER_EIGHT, sgCompare(TIER_EIGHT, maxRank, sumMod));
        // 9. 7点及以下
        return new SangongResult("7点及以下", 1, TIER_LOW, sgCompare(TIER_LOW, maxRank, sumMod));
    }
}
