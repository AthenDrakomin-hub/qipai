package com.poker.platform.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.GameRound;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.enums.GameType;
import com.poker.platform.enums.RoomLevel;
import com.poker.platform.game.GameBetConfig;
import com.poker.platform.game.PokerEngine;
import com.poker.platform.game.PokerEngine.Card;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RoomPlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 六大游戏统一对局服务：自动发牌 + 判定输赢 + 调用结算
 * 同时支持 WebSocket 实时驱动，提供 HTTP 快速演示接口（便于前端联调/单机测试）
 */
@Service
public class GamePlayService {

    private static final Logger log = LoggerFactory.getLogger(GamePlayService.class);

    @Autowired private GameRoomMapper roomMapper;
    @Autowired private RoomPlayerMapper roomPlayerMapper;
    @Autowired private SettlementService settlementService;

    /**
     * 执行一局（全自动：抢庄/发牌/下注/比牌/结算）
     *
     * @param bets 自定义各玩家下注额（userId->bet）。若传 null 则：
     *             通比类 用房间 fixedBetAmount；
     *             其他类 随机下注（基于当前筹码的10%-30%）
     * @param roundNo 指定第几局，不传则 = currentRound + 1
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> playOneRound(Long roomId, Map<Long, Long> bets, Integer roundNo) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new RuntimeException("房间不存在");

        List<RoomPlayer> seatPlayers = roomPlayerMapper.selectList(
                new LambdaQueryWrapper<RoomPlayer>().eq(RoomPlayer::getRoomId, roomId))
                .stream().filter(rp -> rp.getIsObserver() == null || !rp.getIsObserver())
                .filter(rp -> (rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits()) > 0)
                .sorted(Comparator.comparing(RoomPlayer::getSeatNo))
                .collect(Collectors.toList());
        if (seatPlayers.size() < 2) throw new RuntimeException("对局人数不足2人");

        int round = roundNo == null ? (room.getCurrentRound() == null ? 0 : room.getCurrentRound()) + 1 : roundNo;
        if (round > (room.getTotalRounds() == null ? 25 : room.getTotalRounds())) {
            throw new RuntimeException("已达最大局数，请进行总局结算");
        }

        GameType type = GameType.fromCode(room.getGameType());
        RoomLevel level = RoomLevel.fromCode(room.getRoomLevel());
        GameBetConfig.BetParam bp = GameBetConfig.get(type, level);
        // 通比类自动挂机：仅 isAutoPlay=true 的玩家参与本局；不足2人则全部参与
        List<RoomPlayer> effectivePlayers = seatPlayers;
        if (type == GameType.TONGBI_NIUNIU || type == GameType.TONGBI_SANGONG) {
            List<RoomPlayer> auto = seatPlayers.stream()
                    .filter(p -> p.getIsAutoPlay() != null && p.getIsAutoPlay())
                    .collect(Collectors.toList());
            if (auto.size() >= 2) effectivePlayers = auto;
        }
        Map<Long, List<Card>> cardsMap = new HashMap<>();
        List<Card> deck = PokerEngine.newDeck();
        Long bankerId = null;
        int[] diceResult = null;
        Map<Long, Long> playerBets = new HashMap<>();

        switch (type) {
            case TEXAS: {
                // 德州：玩家间比牌通吃，起下注 baseBet + 加注 raiseBet
                bankerId = null;
                Map<Long, List<Card>> holeCards = new HashMap<>();
                for (RoomPlayer rp : effectivePlayers) {
                    List<Card> h = new ArrayList<>();
                    h.add(deck.remove(0)); h.add(deck.remove(0));
                    holeCards.put(rp.getUserId(), h);
                }
                List<Card> board = new ArrayList<>();
                for (int i = 0; i < 5; i++) board.add(deck.remove(0));
                for (RoomPlayer rp : effectivePlayers) {
                    List<Card> all = new ArrayList<>(holeCards.get(rp.getUserId()));
                    all.addAll(board);
                    cardsMap.put(rp.getUserId(), holeCards.get(rp.getUserId())); // 仅返回底牌
                    List<Card> best = pickBest5(all);
                    cardsMap.put(rp.getUserId() + 1000000L, best); // 最佳组合临时存
                    long bet = resolveBet(rp, bets, bp.baseBet + bp.raiseBet);
                    playerBets.put(rp.getUserId(), bet);
                }
                break;
            }

            case JINHUA: {
                // 金花：玩家间比牌通吃。
                // 闷注/看注规则：
                //   闷牌（未看牌）下注 = blindBet；看牌后下注 = lookBet（看牌注为闷注的2倍）
                //   首注为底注 baseBet；已看牌玩家按 lookBet 下注，闷牌玩家按 blindBet 下注
                bankerId = null;
                for (RoomPlayer rp : effectivePlayers) {
                    List<Card> h = Arrays.asList(deck.remove(0), deck.remove(0), deck.remove(0));
                    cardsMap.put(rp.getUserId(), h);
                    boolean looked = rp.getIsLooked() != null && rp.getIsLooked();
                    // 底注 + 本局下注（看牌用 lookBet，闷牌用 blindBet）
                    long roundBet = looked ? bp.lookBet : bp.blindBet;
                    if (roundBet <= 0) roundBet = bp.lookBet > 0 ? bp.lookBet : bp.baseBet;
                    long bet = resolveBet(rp, bets, bp.baseBet + roundBet);
                    playerBets.put(rp.getUserId(), bet);
                }
                break;
            }

            case SANGONG:
            case DOUNIU: {
                // 骰子抢庄：每人3颗骰子，点数大者为庄
                diceResult = PokerEngine.rollDice(effectivePlayers.size() * 3);
                long maxDice = -1;
                int di = 0;
                for (int i = 0; i < effectivePlayers.size(); i++) {
                    int s = diceResult[di] + diceResult[di+1] + diceResult[di+2];
                    di += 3;
                    if (s > maxDice) { maxDice = s; bankerId = effectivePlayers.get(i).getUserId(); }
                }
                int cardCount = (type == GameType.SANGONG) ? 3 : 5;
                for (RoomPlayer rp : effectivePlayers) {
                    List<Card> h = new ArrayList<>();
                    for (int i = 0; i < cardCount; i++) h.add(deck.remove(0));
                    cardsMap.put(rp.getUserId(), h);
                    // 闲家下底注，庄家不下注（通杀）
                    if (!rp.getUserId().equals(bankerId)) {
                        long bet = resolveBet(rp, bets, bp.baseBet);
                        playerBets.put(rp.getUserId(), bet);
                    } else {
                        playerBets.put(rp.getUserId(), 0L);
                    }
                }
                break;
            }

            case TONGBI_NIUNIU:
            case TONGBI_SANGONG: {
                // 通比：无庄，固定下注（房主在区间内设定），大吃小不翻倍，支持自动挂机
                bankerId = null;
                int cc = (type == GameType.TONGBI_SANGONG) ? 3 : 5;
                long fixed = room.getFixedBetAmount() == null ? bp.fixedMin : room.getFixedBetAmount();
                fixed = GameBetConfig.clampFixedBet(type, level, fixed);
                for (RoomPlayer rp : effectivePlayers) {
                    List<Card> h = new ArrayList<>();
                    for (int i = 0; i < cc; i++) h.add(deck.remove(0));
                    cardsMap.put(rp.getUserId(), h);
                    long bet = resolveBet(rp, bets, fixed);
                    playerBets.put(rp.getUserId(), bet);
                }
                break;
            }
        }

        // ======== 判定输赢 ========
        Map<Long, Long> grossProfitMap = computeProfits(
                type, effectivePlayers, bankerId, playerBets, cardsMap, room);

        // ======== 组装 PlayerProfit 并结算 ========
        List<SettlementService.PlayerProfit> profits = new ArrayList<>();
        for (RoomPlayer rp : effectivePlayers) {
            SettlementService.PlayerProfit p = new SettlementService.PlayerProfit();
            p.userId = rp.getUserId();
            p.username = rp.getUsername();
            p.nickname = rp.getNickname();
            p.seatNo = rp.getSeatNo();
            p.betAmount = playerBets.getOrDefault(rp.getUserId(), 0L);
            p.grossProfit = grossProfitMap.getOrDefault(rp.getUserId(), 0L);
            p.cards = cardsMap.get(rp.getUserId()) == null ? "" :
                    cardsMap.get(rp.getUserId()).stream().map(Card::toString).collect(Collectors.joining(","));
            profits.add(p);
        }

        Map<String, Object> dealInfo = new HashMap<>();
        for (Map.Entry<Long, List<Card>> e : cardsMap.entrySet()) {
            // 避免把 "最佳5张组合" 临时键带出去
            if (e.getKey() > 1_000_000) continue;
            dealInfo.put(String.valueOf(e.getKey()),
                    e.getValue().stream().map(Card::toString).collect(Collectors.joining(",")));
        }
        if (diceResult != null) dealInfo.put("dice", diceResult);
        if (bankerId != null) dealInfo.put("banker", bankerId);

        // 牌型与倍数信息（便于前端展示）
        Map<String, Object> cardTypes = new HashMap<>();
        for (RoomPlayer rp : effectivePlayers) {
            List<Card> cs = cardsMap.get(rp.getUserId());
            if (cs == null) continue;
            Map<String, Object> ti = new HashMap<>();
            switch (type) {
                case DOUNIU:
                case TONGBI_NIUNIU: {
                    int niu = PokerEngine.calcNiuValue(cs);
                    ti.put("name", PokerEngine.niuTypeName(niu));
                    ti.put("multiplier", PokerEngine.niuMultiplier(niu));
                    break;
                }
                case SANGONG:
                case TONGBI_SANGONG: {
                    PokerEngine.SangongResult sr = PokerEngine.calcSangongType(cs);
                    ti.put("name", sr.typeName);
                    ti.put("multiplier", sr.multiplier);
                    break;
                }
                case JINHUA: {
                    ti.put("name", jinhuaTypeName(cs));
                    ti.put("multiplier", 1);
                    break;
                }
                case TEXAS: {
                    List<Card> best = cardsMap.get(rp.getUserId() + 1_000_000L);
                    ti.put("name", texasTypeName(best));
                    ti.put("multiplier", 1);
                    break;
                }
                default: break;
            }
            if (!ti.isEmpty()) cardTypes.put(String.valueOf(rp.getUserId()), ti);
        }
        if (!cardTypes.isEmpty()) dealInfo.put("cardTypes", cardTypes);

        GameRound gr = settlementService.settleRound(roomId, round, bankerId, profits, JSON.toJSONString(dealInfo));

        Map<String, Object> result = new HashMap<>();
        result.put("round", round);
        result.put("roundId", gr.getId());
        result.put("gameType", room.getGameType());
        result.put("bankerId", bankerId);
        result.put("dice", diceResult);
        result.put("cards", dealInfo);
        result.put("bets", playerBets);
        result.put("profits", profits.stream().collect(Collectors.toMap(p -> p.userId, p -> p)));
        result.put("roundTurnover", gr.getRoundTurnover());
        result.put("roundRake", gr.getRoundRake());

        // 达最大局数自动总局结算
        int totalRounds = room.getTotalRounds() == null ? 25 : room.getTotalRounds();
        if (round >= totalRounds) {
            Map<String, Object> finalSettle = settlementService.settleRoom(roomId);
            result.put("finalSettlement", finalSettle);
        }
        return result;
    }

    /** 解析玩家下注：优先用自定义 bets，否则用默认 defaultBet，且不超过自身筹码 */
    private long resolveBet(RoomPlayer rp, Map<Long, Long> bets, long defaultBet) {
        long me = rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits();
        if (bets != null && bets.containsKey(rp.getUserId())) {
            return Math.min(me, Math.max(0L, bets.get(rp.getUserId())));
        }
        return Math.min(me, Math.max(0L, defaultBet));
    }

    /** 选德州最佳5张（C(7,5)=21） */
    private List<Card> pickBest5(List<Card> seven) {
        long best = -1; List<Card> bestCards = null;
        int[] idx = {0,1,2,3,4,5,6};
        for (int a=0;a<7;a++) for (int b=a+1;b<7;b++) for (int c=b+1;c<7;c++)
            for (int d=c+1;d<7;d++) for (int e=d+1;e<7;e++) {
                List<Card> five = Arrays.asList(seven.get(a),seven.get(b),seven.get(c),seven.get(d),seven.get(e));
                long s = PokerEngine.rate5Cards(five);
                if (s > best) { best = s; bestCards = five; }
            }
        return bestCards;
    }

    /**
     * 计算单局每位玩家毛输赢：
     * - 抢庄牛/抢庄三公：庄闲比牌，按闲家牌型倍数 × 底注结算，封顶 cap
     * - 通比牛/通比三公/金花/德州：最大牌力通吃所有玩家下注（不翻倍）
     */
    private Map<Long, Long> computeProfits(GameType type, List<RoomPlayer> players,
                                            Long bankerId, Map<Long, Long> bets,
                                            Map<Long, List<Card>> cardsMap, GameRoom room) {
        Map<Long, Long> profit = new HashMap<>();
        for (RoomPlayer rp : players) profit.put(rp.getUserId(), 0L);
        if (players.size() < 2) return profit;

        RoomLevel level = RoomLevel.fromCode(room.getRoomLevel());
        GameBetConfig.BetParam bp = GameBetConfig.get(type, level);

        // 计算各玩家牌力值（用于通吃模式排序）
        Map<Long, Long> power = new HashMap<>();
        for (RoomPlayer rp : players) {
            List<Card> cs = cardsMap.get(rp.getUserId());
            if (cs == null) { power.put(rp.getUserId(), 0L); continue; }
            long v;
            switch (type) {
                case TEXAS: {
                    List<Card> best = cardsMap.get(rp.getUserId() + 1_000_000L);
                    v = PokerEngine.rate5Cards(best);
                    break;
                }
                case JINHUA: {
                    v = rate3Cards(cs);
                    break;
                }
                case SANGONG:
                case TONGBI_SANGONG: {
                    v = PokerEngine.calcSangongType(cs).compareValue;
                    break;
                }
                case DOUNIU:
                case TONGBI_NIUNIU: {
                    int niu = PokerEngine.calcNiuValue(cs);
                    List<Card> sorted = new ArrayList<>(cs);
                    sorted.sort((x, y) -> {
                        int rx = x.rank == 14 ? 1 : x.rank;
                        int ry = y.rank == 14 ? 1 : y.rank;
                        if (rx != ry) return ry - rx;
                        return x.suit - y.suit;
                    });
                    Card m = sorted.get(0);
                    int mr = m.rank == 14 ? 1 : m.rank;
                    int ms = 4 - m.suit; // ♠0 -> 4
                    v = (long) niu * 10000 + mr * 100L + ms;
                    break;
                }
                default: v = 0;
            }
            power.put(rp.getUserId(), v);
        }

        if (bankerId != null && (type == GameType.DOUNIU || type == GameType.SANGONG)) {
            // ===== 抢庄模式：庄闲比牌，按闲家牌型倍数 × 底注，封顶 cap =====
            RoomPlayer banker = null;
            for (RoomPlayer rp : players) {
                if (rp.getUserId().equals(bankerId)) { banker = rp; break; }
            }
            if (banker == null) banker = players.get(0);
            long bankerStack = banker.getCurrentCredits() == null ? 0L : banker.getCurrentCredits();

            for (RoomPlayer opp : players) {
                if (opp.getUserId().equals(banker.getUserId())) continue;
                long stake = bets.getOrDefault(opp.getUserId(), 0L); // 闲家底注
                long oppStack = opp.getCurrentCredits() == null ? 0L : opp.getCurrentCredits();
                if (stake <= 0) continue;

                // 闲家牌型倍数 + 庄闲比牌
                int mult;
                long cmp;
                if (type == GameType.DOUNIU) {
                    int oppNiu = PokerEngine.calcNiuValue(cardsMap.get(opp.getUserId()));
                    int bankerNiu = PokerEngine.calcNiuValue(cardsMap.get(banker.getUserId()));
                    mult = PokerEngine.niuMultiplier(oppNiu);
                    cmp = Long.compare(bankerNiu, oppNiu);
                } else {
                    PokerEngine.SangongResult oppRes = PokerEngine.calcSangongType(cardsMap.get(opp.getUserId()));
                    PokerEngine.SangongResult bankerRes = PokerEngine.calcSangongType(cardsMap.get(banker.getUserId()));
                    mult = oppRes.multiplier;
                    cmp = Long.compare(bankerRes.compareValue, oppRes.compareValue);
                }
                long amount = stake * mult;
                if (bp.cap > 0 && amount > bp.cap) amount = bp.cap; // 封顶
                amount = Math.min(amount, oppStack);    // 闲家最多输自己筹码
                amount = Math.min(amount, bankerStack); // 庄家最多输自己筹码
                if (amount <= 0 || cmp == 0) continue;
                if (cmp > 0) { // 庄赢
                    profit.put(banker.getUserId(), profit.get(banker.getUserId()) + amount);
                    profit.put(opp.getUserId(), profit.get(opp.getUserId()) - amount);
                } else { // 闲赢
                    profit.put(banker.getUserId(), profit.get(banker.getUserId()) - amount);
                    profit.put(opp.getUserId(), profit.get(opp.getUserId()) + amount);
                }
            }
        } else {
            // ===== 通吃模式：通比牛/通比三公/金花/德州，最大牌力通吃所有下注（不翻倍）=====
            long maxPower = -1;
            for (long v : power.values()) if (v > maxPower) maxPower = v;
            List<Long> winners = new ArrayList<>();
            for (RoomPlayer rp : players) {
                if (power.get(rp.getUserId()) == maxPower) winners.add(rp.getUserId());
            }
            // 输家把下注放入奖池
            long totalPot = 0;
            for (RoomPlayer rp : players) {
                if (!winners.contains(rp.getUserId())) {
                    long b = bets.getOrDefault(rp.getUserId(), 0L);
                    long lose = Math.min(b, rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits());
                    totalPot += lose;
                    profit.put(rp.getUserId(), profit.get(rp.getUserId()) - lose);
                }
            }
            // 赢家平分奖池，余数给首个赢家
            long share = totalPot / winners.size();
            for (Long w : winners) profit.put(w, profit.get(w) + share);
            long remainder = totalPot - share * winners.size();
            if (remainder > 0) profit.put(winners.get(0), profit.get(winners.get(0)) + remainder);
        }
        return profit;
    }

    /** 3张牌评级（炸金花）：同花顺>三条>顺>同花>对子>高牌 */
    private long rate3Cards(List<Card> c) {
        boolean flush = c.get(0).suit == c.get(1).suit && c.get(1).suit == c.get(2).suit;
        int[] r = c.stream().mapToInt(x -> x.rank).sorted().toArray();
        boolean straight = false; int sh = 0;
        if (r[2] - r[0] == 2 && r[0] != r[1] && r[1] != r[2]) { straight = true; sh = r[2]; }
        else if (Arrays.equals(r, new int[]{2,3,14})) { straight = true; sh = 3; }
        boolean three = r[0] == r[2];
        boolean pair = r[0] == r[1] || r[1] == r[2];
        int type;
        if (flush && straight) type = 5;           // 同花顺
        else if (three) type = 4;                  // 豹子(三条)
        else if (straight) type = 3;               // 顺子
        else if (flush) type = 2;                  // 同花
        else if (pair) type = 1;                   // 对子
        else type = 0;
        long p = r[2] * 10000L + r[1] * 100L + r[0];
        return (long) type * 1_000_000L + p;
    }

    /** 金花牌型名（基于 rate3Cards 的 type 位） */
    private String jinhuaTypeName(List<Card> c) {
        long score = rate3Cards(c);
        int type = (int) (score / 1_000_000L);
        switch (type) {
            case 5: return "顺金";
            case 4: return "豹子";
            case 3: return "顺子";
            case 2: return "金花";
            case 1: return "对子";
            default: return "单牌";
        }
    }

    /** 德州牌型名（基于 rate5Cards 的 type 位） */
    private String texasTypeName(List<Card> c) {
        if (c == null) return "高牌";
        long score = PokerEngine.rate5Cards(c);
        int type = (int) (score / 1_000_000L);
        switch (type) {
            case 8: return "同花顺";
            case 7: return "四条";
            case 6: return "葫芦";
            case 5: return "同花";
            case 4: return "顺子";
            case 3: return "三条";
            case 2: return "两对";
            case 1: return "一对";
            default: return "高牌";
        }
    }
}
