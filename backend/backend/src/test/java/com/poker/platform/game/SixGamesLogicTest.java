package com.poker.platform.game;

import com.poker.platform.enums.GameType;
import com.poker.platform.enums.RoomLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 六大游戏核心逻辑测试用例
 *
 * 覆盖范围：
 * 1. 抢庄牛 - 倍数计算(niuMultiplier) + 牛值计算(calcNiuValue)
 * 2. 通比牛 - 牛值计算 + 通吃比牌
 * 3. 抢庄三公 - 牌型判定与倍数(calcSangongType)
 * 4. 通比三公 - 点数计算 + 通吃比牌
 * 5. 金花 - 5张牌型评级(rate5Cards)
 * 6. 德州 - 5张牌型评级(rate5Cards)
 * 7. 下注参数 - GameBetConfig 六大游戏三档等级
 *
 * 牌的构造：new Card(id)，id=0~51
 * rank = (id % 13) + 2  →  2..10 J(11) Q(12) K(13) A(14)
 * suit = id / 13       →  0♠ 1♥ 2♣ 3♦
 * 点数：J/Q/K=10, A=1, 2-10=本身
 */
@DisplayName("六大游戏核心逻辑测试")
class SixGamesLogicTest {

    // ============ 辅助：构造手牌 ============
    // 按 rank 和 suit 构造 Card（rank:2-14, suit:0-3）
    private PokerEngine.Card card(int rank, int suit) {
        return new PokerEngine.Card((rank - 2) + suit * 13);
    }

    private List<PokerEngine.Card> cards(PokerEngine.Card... cs) {
        return Arrays.asList(cs);
    }

    // ============ 1. 抢庄牛倍数测试 ============
    @Nested
    @DisplayName("1. 抢庄牛 - 倍数计算 niuMultiplier")
    class NiuMultiplierTest {

        @Test
        @DisplayName("牛牛 ×5倍")
        void niuNiu_5x() {
            // 5张10点牌，任意3张和%10=0，剩余2张和%10=0 → 牛牛
            assertThat(PokerEngine.niuMultiplier(10)).isEqualTo(5);
        }

        @Test
        @DisplayName("牛9 ×4倍")
        void niu9_4x() {
            assertThat(PokerEngine.niuMultiplier(9)).isEqualTo(4);
        }

        @Test
        @DisplayName("牛8 ×3倍")
        void niu8_3x() {
            assertThat(PokerEngine.niuMultiplier(8)).isEqualTo(3);
        }

        @Test
        @DisplayName("牛7 ×1倍（正常比大小）")
        void niu7_1x() {
            assertThat(PokerEngine.niuMultiplier(7)).isEqualTo(1);
        }

        @Test
        @DisplayName("牛1 ×1倍")
        void niu1_1x() {
            assertThat(PokerEngine.niuMultiplier(1)).isEqualTo(1);
        }

        @Test
        @DisplayName("没牛(0) ×1倍")
        void noNiu_1x() {
            assertThat(PokerEngine.niuMultiplier(0)).isEqualTo(1);
        }

        @Test
        @DisplayName("牛型名称显示")
        void niuTypeName() {
            assertThat(PokerEngine.niuTypeName(10)).isEqualTo("牛牛");
            assertThat(PokerEngine.niuTypeName(9)).isEqualTo("牛9");
            assertThat(PokerEngine.niuTypeName(8)).isEqualTo("牛8");
            assertThat(PokerEngine.niuTypeName(7)).isEqualTo("牛7");
            assertThat(PokerEngine.niuTypeName(0)).isEqualTo("没牛");
        }
    }

    // ============ 2. 斗牛牛值计算测试 ============
    @Nested
    @DisplayName("2. 斗牛/通比牛 - 牛值计算 calcNiuValue")
    class NiuValueTest {

        @Test
        @DisplayName("牛牛：5张全10点牌")
        void niuNiu() {
            // ♠10 ♥10 ♣10 ♦10 ♠J → 全部10点
            List<PokerEngine.Card> cards = cards(
                card(10, 0), card(10, 1), card(10, 2), card(10, 3), card(11, 0)
            );
            assertThat(PokerEngine.calcNiuValue(cards)).isEqualTo(10); // 牛牛
        }

        @Test
        @DisplayName("牛9：3张凑10倍数，剩余2张和%10=9")
        void niu9() {
            // 5+5+10=20%10=0，剩余3+6=9
            List<PokerEngine.Card> cards = cards(
                card(5, 0), card(5, 1), card(10, 0), card(3, 0), card(6, 0)
            );
            assertThat(PokerEngine.calcNiuValue(cards)).isEqualTo(9);
        }

        @Test
        @DisplayName("没牛：任意3张都无法凑成10倍数")
        void noNiu() {
            // 2+3+4=9, 2+3+5=10... 让我构造一个确实没牛的: 2,3,4,6,7
            // 2+3+4=9, 2+3+6=11, 2+3+7=12, 2+4+6=12, 2+4+7=13, 2+6+7=15, 3+4+6=13, 3+4+7=14, 3+6+7=16, 4+6+7=17 → 都不是10倍数
            List<PokerEngine.Card> cards = cards(
                card(2, 0), card(3, 0), card(4, 0), card(6, 0), card(7, 0)
            );
            assertThat(PokerEngine.calcNiuValue(cards)).isEqualTo(0); // 没牛
        }

        @Test
        @DisplayName("牛8：3张凑10倍数，剩余2张和%10=8")
        void niu8() {
            // 10+10+10=30%10=0，剩余2+6=8
            List<PokerEngine.Card> cards = cards(
                card(10, 0), card(10, 1), card(10, 2), card(2, 0), card(6, 0)
            );
            assertThat(PokerEngine.calcNiuValue(cards)).isEqualTo(8);
        }
    }

    // ============ 3. 抢庄三公牌型与倍数测试 ============
    @Nested
    @DisplayName("3. 抢庄三公 - 牌型判定 calcSangongType")
    class SangongTypeTest {

        @Test
        @DisplayName("大三公 ×6：三张公牌(J/Q/K)且点数完全相同")
        void bigSangong_6x() {
            // J J J → 大三公
            List<PokerEngine.Card> cards = cards(card(11, 0), card(11, 1), card(11, 2));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("大三公");
            assertThat(r.multiplier).isEqualTo(6);
        }

        @Test
        @DisplayName("小三公 ×5：三张公牌，恰好两张点数相同")
        void smallSangong_5x() {
            // J J K → 小三公（两张J相同）
            List<PokerEngine.Card> cards = cards(card(11, 0), card(11, 1), card(13, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("小三公");
            assertThat(r.multiplier).isEqualTo(5);
        }

        @Test
        @DisplayName("混三公 ×5：三张公牌且点数各不相同")
        void mixedSangong_5x() {
            // J Q K → 混三公
            List<PokerEngine.Card> cards = cards(card(11, 0), card(12, 0), card(13, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("混三公");
            assertThat(r.multiplier).isEqualTo(5);
        }

        @Test
        @DisplayName("豹子 ×4：三张牌点数相同（非公牌）")
        void leopard_4x() {
            // 5 5 5 → 豹子
            List<PokerEngine.Card> cards = cards(card(5, 0), card(5, 1), card(5, 2));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("豹子");
            assertThat(r.multiplier).isEqualTo(4);
        }

        @Test
        @DisplayName("双公9点 ×3：恰好两张公牌，第三张为9")
        void doubleGong9_3x() {
            // J J 9 → 双公9点
            List<PokerEngine.Card> cards = cards(card(11, 0), card(11, 1), card(9, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("双公9点");
            assertThat(r.multiplier).isEqualTo(3);
        }

        @Test
        @DisplayName("双公8点 ×2：恰好两张公牌，第三张为8")
        void doubleGong8_2x() {
            // J Q 8 → 双公8点
            List<PokerEngine.Card> cards = cards(card(11, 0), card(12, 0), card(8, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("双公8点");
            assertThat(r.multiplier).isEqualTo(2);
        }

        @Test
        @DisplayName("9点 ×3：单公或无公，点数和取个位为9")
        void nine_3x() {
            // J(10) + 5 + 4 = 19%10 = 9 → 9点
            List<PokerEngine.Card> cards = cards(card(11, 0), card(5, 0), card(4, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("9点");
            assertThat(r.multiplier).isEqualTo(3);
        }

        @Test
        @DisplayName("8点 ×2：单公或无公，点数和取个位为8")
        void eight_2x() {
            // J(10) + 5 + 3 = 18%10 = 8 → 8点
            List<PokerEngine.Card> cards = cards(card(11, 0), card(5, 0), card(3, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("8点");
            assertThat(r.multiplier).isEqualTo(2);
        }

        @Test
        @DisplayName("7点及以下 ×1：单公或无公，点数0~7")
        void sevenOrBelow_1x() {
            // 2 + 3 + 5 = 10%10 = 0 → 7点及以下
            List<PokerEngine.Card> cards = cards(card(2, 0), card(3, 0), card(5, 0));
            PokerEngine.SangongResult r = PokerEngine.calcSangongType(cards);
            assertThat(r.typeName).isEqualTo("7点及以下");
            assertThat(r.multiplier).isEqualTo(1);
        }

        @Test
        @DisplayName("三公比牌：大三公 > 豹子 > 9点 > 7点及以下")
        void sangongCompare() {
            PokerEngine.SangongResult big = PokerEngine.calcSangongType(cards(card(11, 0), card(11, 1), card(11, 2)));
            PokerEngine.SangongResult leopard = PokerEngine.calcSangongType(cards(card(5, 0), card(5, 1), card(5, 2)));
            PokerEngine.SangongResult nine = PokerEngine.calcSangongType(cards(card(11, 0), card(5, 0), card(4, 0)));
            PokerEngine.SangongResult low = PokerEngine.calcSangongType(cards(card(2, 0), card(3, 0), card(5, 0)));
            assertThat(big.compareValue).isGreaterThan(leopard.compareValue);
            assertThat(leopard.compareValue).isGreaterThan(nine.compareValue);
            assertThat(nine.compareValue).isGreaterThan(low.compareValue);
        }
    }

    // ============ 4. 金花/德州牌型评级测试 ============
    @Nested
    @DisplayName("4. 金花/德州 - 5张牌型评级 rate5Cards")
    class Rate5CardsTest {

        @Test
        @DisplayName("同花顺 > 四条 > 葫芦 > 同花 > 顺子 > 三条 > 两对 > 一对 > 高牌")
        void handRanking() {
            // 同花顺：♠5 ♠6 ♠7 ♠8 ♠9
            long straightFlush = PokerEngine.rate5Cards(cards(
                card(5, 0), card(6, 0), card(7, 0), card(8, 0), card(9, 0)));
            // 四条：5 5 5 5 3
            long fourKind = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(5, 2), card(5, 3), card(3, 0)));
            // 葫芦：5 5 5 3 3
            long fullHouse = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(5, 2), card(3, 0), card(3, 1)));
            // 同花：♠5 ♠3 ♠7 ♠9 ♠J
            long flush = PokerEngine.rate5Cards(cards(
                card(5, 0), card(3, 0), card(7, 0), card(9, 0), card(11, 0)));
            // 顺子：5 6 7 8 9（不同花）
            long straight = PokerEngine.rate5Cards(cards(
                card(5, 0), card(6, 1), card(7, 0), card(8, 1), card(9, 0)));
            // 三条：5 5 5 3 7
            long threeKind = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(5, 2), card(3, 0), card(7, 0)));
            // 两对：5 5 3 3 7
            long twoPair = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(3, 0), card(3, 1), card(7, 0)));
            // 一对：5 5 3 7 9
            long onePair = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(3, 0), card(7, 0), card(9, 0)));
            // 高牌：5 3 7 9 J（不同花）
            long highCard = PokerEngine.rate5Cards(cards(
                card(5, 0), card(3, 1), card(7, 0), card(9, 1), card(11, 0)));

            assertThat(straightFlush).isGreaterThan(fourKind);
            assertThat(fourKind).isGreaterThan(fullHouse);
            assertThat(fullHouse).isGreaterThan(flush);
            assertThat(flush).isGreaterThan(straight);
            assertThat(straight).isGreaterThan(threeKind);
            assertThat(threeKind).isGreaterThan(twoPair);
            assertThat(twoPair).isGreaterThan(onePair);
            assertThat(onePair).isGreaterThan(highCard);
        }

        @Test
        @DisplayName("德州场景：葫芦赢三条")
        void texasFullHouseBeatsThreeKind() {
            // 葫芦：5 5 5 3 3
            long fullHouse = PokerEngine.rate5Cards(cards(
                card(4, 0), card(4, 1), card(4, 2), card(3, 0), card(3, 1)));
            // 三条：4 4 4 7 9（三张相同+两张不同单牌）
            long threeKind = PokerEngine.rate5Cards(cards(
                card(4, 0), card(4, 1), card(4, 2), card(7, 0), card(9, 0)));
            assertThat(fullHouse).isGreaterThan(threeKind);
        }

        @Test
        @DisplayName("金花场景：对子赢单牌")
        void jinhuaPairBeatsHigh() {
            long pair = PokerEngine.rate5Cards(cards(
                card(5, 0), card(5, 1), card(8, 0), card(3, 0), card(10, 0)));
            long high = PokerEngine.rate5Cards(cards(
                card(5, 0), card(8, 1), card(3, 0), card(10, 0), card(6, 1)));
            assertThat(pair).isGreaterThan(high);
        }
    }

    // ============ 5. 下注参数配置测试 ============
    @Nested
    @DisplayName("5. GameBetConfig - 六大游戏下注参数")
    class BetConfigTest {

        @Test
        @DisplayName("抢庄牛：初级5-10-50 / 高级20-50-200 / 顶级50-100-500")
        void douniuBetParams() {
            assertThat(GameBetConfig.get(GameType.DOUNIU, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(5); assertThat(p.raiseBet).isEqualTo(10); assertThat(p.cap).isEqualTo(50); });
            assertThat(GameBetConfig.get(GameType.DOUNIU, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(20); assertThat(p.raiseBet).isEqualTo(50); assertThat(p.cap).isEqualTo(200); });
            assertThat(GameBetConfig.get(GameType.DOUNIU, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(50); assertThat(p.raiseBet).isEqualTo(100); assertThat(p.cap).isEqualTo(500); });
        }

        @Test
        @DisplayName("通比牛：初级5-50 / 高级20-200 / 顶级50-500（固定下注区间）")
        void tongbiNiuBetParams() {
            assertThat(GameBetConfig.get(GameType.TONGBI_NIUNIU, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(5); assertThat(p.fixedMax).isEqualTo(50); });
            assertThat(GameBetConfig.get(GameType.TONGBI_NIUNIU, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(20); assertThat(p.fixedMax).isEqualTo(200); });
            assertThat(GameBetConfig.get(GameType.TONGBI_NIUNIU, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(50); assertThat(p.fixedMax).isEqualTo(500); });
        }

        @Test
        @DisplayName("抢庄三公：初级5-10-30 / 高级20-50-200 / 顶级50-100-500")
        void sangongBetParams() {
            assertThat(GameBetConfig.get(GameType.SANGONG, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(5); assertThat(p.raiseBet).isEqualTo(10); assertThat(p.cap).isEqualTo(30); });
            assertThat(GameBetConfig.get(GameType.SANGONG, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(20); assertThat(p.raiseBet).isEqualTo(50); assertThat(p.cap).isEqualTo(200); });
            assertThat(GameBetConfig.get(GameType.SANGONG, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(50); assertThat(p.raiseBet).isEqualTo(100); assertThat(p.cap).isEqualTo(500); });
        }

        @Test
        @DisplayName("通比三公：初级5-50 / 高级20-200 / 顶级50-500（固定下注区间）")
        void tongbiSangongBetParams() {
            assertThat(GameBetConfig.get(GameType.TONGBI_SANGONG, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(5); assertThat(p.fixedMax).isEqualTo(50); });
            assertThat(GameBetConfig.get(GameType.TONGBI_SANGONG, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(20); assertThat(p.fixedMax).isEqualTo(200); });
            assertThat(GameBetConfig.get(GameType.TONGBI_SANGONG, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.fixedMin).isEqualTo(50); assertThat(p.fixedMax).isEqualTo(500); });
        }

        @Test
        @DisplayName("金花：初级5底5闷10看 / 高级10底20闷40看 / 顶级50底50闷100看")
        void jinhuaBetParams() {
            assertThat(GameBetConfig.get(GameType.JINHUA, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(5); assertThat(p.blindBet).isEqualTo(5); assertThat(p.lookBet).isEqualTo(10); });
            assertThat(GameBetConfig.get(GameType.JINHUA, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(10); assertThat(p.blindBet).isEqualTo(20); assertThat(p.lookBet).isEqualTo(40); });
            assertThat(GameBetConfig.get(GameType.JINHUA, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(50); assertThat(p.blindBet).isEqualTo(50); assertThat(p.lookBet).isEqualTo(100); });
        }

        @Test
        @DisplayName("德州：初级5-10-50 / 高级10-50-100 / 顶级50-100-200")
        void texasBetParams() {
            assertThat(GameBetConfig.get(GameType.TEXAS, RoomLevel.PRIMARY))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(5); assertThat(p.raiseBet).isEqualTo(10); assertThat(p.cap).isEqualTo(50); });
            assertThat(GameBetConfig.get(GameType.TEXAS, RoomLevel.ADVANCED))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(10); assertThat(p.raiseBet).isEqualTo(50); assertThat(p.cap).isEqualTo(100); });
            assertThat(GameBetConfig.get(GameType.TEXAS, RoomLevel.PREMIUM))
                .satisfies(p -> { assertThat(p.baseBet).isEqualTo(50); assertThat(p.raiseBet).isEqualTo(100); assertThat(p.cap).isEqualTo(200); });
        }

        @Test
        @DisplayName("通比类固定下注校验：越界取边界值")
        void clampFixedBet() {
            // 初级通比牛 5-50：输入3取5，输入100取50，输入20原样返回
            assertThat(GameBetConfig.clampFixedBet(GameType.TONGBI_NIUNIU, RoomLevel.PRIMARY, 3)).isEqualTo(5);
            assertThat(GameBetConfig.clampFixedBet(GameType.TONGBI_NIUNIU, RoomLevel.PRIMARY, 100)).isEqualTo(50);
            assertThat(GameBetConfig.clampFixedBet(GameType.TONGBI_NIUNIU, RoomLevel.PRIMARY, 20)).isEqualTo(20);
        }
    }

    // ============ 6. 比牌逻辑测试 ============
    @Nested
    @DisplayName("6. 比牌逻辑")
    class CompareTest {

        @Test
        @DisplayName("斗牛比牌：牛9赢牛7")
        void niuCompare() {
            // 牛9：5+5+10=20%10=0, 剩余3+6=9
            List<PokerEngine.Card> niu9 = cards(
                card(5, 0), card(5, 1), card(10, 0), card(3, 0), card(6, 0));
            // 牛7：5+5+10=20%10=0, 剩余3+4=7
            List<PokerEngine.Card> niu7 = cards(
                card(5, 0), card(5, 1), card(10, 0), card(3, 0), card(4, 0));
            assertThat(PokerEngine.compareNiu(niu9, niu7)).isGreaterThan(0);
        }

        @Test
        @DisplayName("三公比牌：大三公(JJJ)赢豹子(555)")
        void sangongCompare() {
            List<PokerEngine.Card> big = cards(card(11, 0), card(11, 1), card(11, 2)); // 大三公
            List<PokerEngine.Card> leopard = cards(card(5, 0), card(5, 1), card(5, 2)); // 豹子
            assertThat(PokerEngine.compareSangong(big, leopard)).isGreaterThan(0);
        }
    }
}
