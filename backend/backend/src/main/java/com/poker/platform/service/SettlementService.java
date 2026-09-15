package com.poker.platform.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.config.PlatformConfig;
import com.poker.platform.entity.*;
import com.poker.platform.enums.RoomStatus;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对局结算核心服务
 *  1. 单局结算 -> 赢家盈利3%抽水
 *  2. 房间25局总局结算 -> 代理总流水3%水费（房主扣3% + 返佣1%） + 总代理推广分润
 *  3. 玩家积分/输赢 流水记账
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    @Autowired private GameRoomMapper roomMapper;
    @Autowired private RoomPlayerMapper roomPlayerMapper;
    @Autowired private GameRoundMapper roundMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private CreditLogMapper creditLogMapper;
    @Autowired private AgentWaterFeeLogMapper waterFeeLogMapper;
    @Autowired private GeneralAgentCommissionMapper commissionMapper;
    @Autowired private RakeRebateLogMapper rakeRebateLogMapper;
    @Autowired private PlatformConfig platformConfig;
    @Autowired private CreditService creditService;

    /** 单玩家输赢明细 */
    public static class PlayerProfit {
        public Long userId;
        public String username;
        public String nickname;
        public Integer seatNo;
        /** 本局下注额 */
        public long betAmount;
        /** 本局原始输赢（未扣抽水）正数为赢，负数为输 */
        public long grossProfit;
        /** 平台抽水（仅赢家盈利部分扣） */
        public long rake;
        /** 本局净输赢 = grossProfit - rake */
        public long netProfit;
        /** 发牌详情（内部记录） */
        public String cards;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getSeatNo() { return seatNo; }
        public void setSeatNo(Integer seatNo) { this.seatNo = seatNo; }
        public long getBetAmount() { return betAmount; }
        public void setBetAmount(long betAmount) { this.betAmount = betAmount; }
        public long getGrossProfit() { return grossProfit; }
        public void setGrossProfit(long grossProfit) { this.grossProfit = grossProfit; }
        public long getRake() { return rake; }
        public void setRake(long rake) { this.rake = rake; }
        public long getNetProfit() { return netProfit; }
        public void setNetProfit(long netProfit) { this.netProfit = netProfit; }
        public String getCards() { return cards; }
        public void setCards(String cards) { this.cards = cards; }
    }

    /**
     * 执行单局结算：
     *  - 更新room玩家currentCredits / totalProfit
     *  - 累计room总流水/总抽水
     *  - 记录game_round
     *  - 每玩家积分变动流水
     * @return 返回本局详细信息
     */
    @Transactional(rollbackFor = Exception.class)
    public GameRound settleRound(Long roomId, int roundNo, Long bankerId,
                                 List<PlayerProfit> profits, String dealRecordJson) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");

        Map<Long, RoomPlayer> rpMap = roomPlayerMapper.selectList(
                new LambdaQueryWrapper<RoomPlayer>().eq(RoomPlayer::getRoomId, roomId))
                .stream().filter(rp -> rp.getIsObserver() == null || !rp.getIsObserver())
                .collect(Collectors.toMap(RoomPlayer::getUserId, rp -> rp));

        long roundTurnover = 0;
        long roundRake = 0;
        List<Long> winners = new ArrayList<>();
        List<Long> losers = new ArrayList<>();
        Map<Long, Long> settleMap = new HashMap<>(); // userId -> netProfit

        // 1. 计算抽水和净输赢
        for (PlayerProfit p : profits) {
            roundTurnover += Math.abs(p.betAmount);
            if (p.grossProfit > 0) {
                // 按赢家盈利的 rakePercent% 抽水（向下取整）
                p.rake = p.grossProfit * platformConfig.getRakePercent() / 100;
                winners.add(p.userId);
            } else if (p.grossProfit < 0) {
                losers.add(p.userId);
            }
            p.netProfit = p.grossProfit - p.rake;
            roundRake += p.rake;
            settleMap.put(p.userId, p.netProfit);
        }

        // 2. 更新 room_player 余额 / 总输赢
        for (PlayerProfit p : profits) {
            RoomPlayer rp = rpMap.get(p.userId);
            if (rp == null) continue;
            rp.setCurrentCredits((rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits()) + p.netProfit);
            rp.setTotalProfit((rp.getTotalProfit() == null ? 0L : rp.getTotalProfit()) + p.netProfit);
            roomPlayerMapper.updateById(rp);
        }

        // 3. 累计房间总流水/总抽水
        room.setTotalTurnover((room.getTotalTurnover() == null ? 0L : room.getTotalTurnover()) + roundTurnover);
        room.setTotalRake((room.getTotalRake() == null ? 0L : room.getTotalRake()) + roundRake);
        room.setCurrentRound(roundNo);
        if (roundNo > 0 && roundNo >= (room.getTotalRounds() == null ? 25 : room.getTotalRounds())) {
            room.setStatus(RoomStatus.SETTLED.getCode());
        } else if (RoomStatus.WAITING.getCode().equals(room.getStatus())) {
            room.setStatus(RoomStatus.PLAYING.getCode());
        }
        roomMapper.updateById(room);

        // 4. 写 game_round
        GameRound round = new GameRound();
        round.setRoomId(roomId);
        round.setRoomNo(room.getRoomNo());
        round.setRoundNo(roundNo);
        round.setRoundTurnover(roundTurnover);
        round.setRoundRake(roundRake);
        round.setBankerId(bankerId);
        round.setWinnerIds(winners.stream().map(String::valueOf).collect(Collectors.joining(",")));
        round.setLoserIds(losers.stream().map(String::valueOf).collect(Collectors.joining(",")));
        round.setSettlementDetail(JSON.toJSONString(profits));
        round.setDealRecord(dealRecordJson);
        roundMapper.insert(round);

        // 5. 写 credit_log（对局输赢 type=3；平台抽水 type=4）
        for (PlayerProfit p : profits) {
            User u = userMapper.selectById(p.userId);
            if (u == null) continue;
            long before = u.getCredits() == null ? 0L : u.getCredits();
            long after = before + p.netProfit;
            if (after < 0) after = 0;
            u.setCredits(after);
            userMapper.updateById(u);

            if (p.netProfit != 0) {
                CreditLog l = new CreditLog();
                l.setUserId(p.userId);
                l.setUsername(p.username);
                l.setChangeType(3);
                l.setChangeValue(p.netProfit);
                l.setBeforeValue(before);
                l.setAfterValue(after);
                l.setRoomId(roomId);
                l.setRoomNo(room.getRoomNo());
                l.setRoundId(round.getId());
                l.setRemark("第" + roundNo + "局结算，下注:" + p.betAmount + " 净输赢:" + p.netProfit
                        + (p.rake > 0 ? " 平台抽水:" + p.rake : ""));
                creditLogMapper.insert(l);
            }
            // 平台抽水单独记一条：目标用户0(平台)，这里以房主视角记录为4类汇总；同时以0虚拟账户记
            if (p.rake > 0) {
                CreditLog rake = new CreditLog();
                rake.setUserId(0L);
                rake.setUsername("PLATFORM");
                rake.setChangeType(4);
                rake.setChangeValue(p.rake);
                rake.setBeforeValue(0L);
                rake.setAfterValue(p.rake);
                rake.setRoomId(roomId);
                rake.setRoomNo(room.getRoomNo());
                rake.setRoundId(round.getId());
                rake.setRemark("第" + roundNo + "局平台抽水(赢家盈利" + platformConfig.getRakePercent() + "%) 玩家:" + p.username);
                creditLogMapper.insert(rake);
            }
        }

        log.info("单局结算完成 room={} round={} turnover={} rake={}", room.getRoomNo(), roundNo, roundTurnover, roundRake);
        return round;
    }

    /**
     * 25局总局结算（新规则）：
     *  1. 将所有 room_player 的 currentCredits 退还到玩家 account
     *  2. 房主扣费（总代理或二级代理）：总流水 × 3% （水费 = 总流水×3%）
     *  3. 系统自动返佣：总流水 × 1% 信用分，返还房主
     *     —— 写 rake_rebate_log 历史账单（房间号/扣除多少/系统返还信用分）
     *  4. 总代理分润：若房主是二级代理且其上级为总代理，则上级总代理得推广分润
     *  5. 状态 -> 已结算
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleRoom(Long roomId) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        // 幂等：已结算 / 已关闭 直接返回空结构 + 幂等标记；避免重复扣水费/返佣
        if (RoomStatus.SETTLED.getCode().equals(room.getStatus())
                || RoomStatus.CLOSED.getCode().equals(room.getStatus())) {
            Map<String, Object> res = new HashMap<>();
            res.put("roomNo", room.getRoomNo());
            res.put("totalTurnover", room.getTotalTurnover());
            res.put("totalRake", room.getTotalRake());
            res.put("ownerDeducted", 0);
            res.put("ownerRebate", 0);
            res.put("ownerNetCost", 0);
            res.put("billStatus", "IDEMPOTENT");
            res.put("billFailReason", "房间已结算/已关闭，重复调用直接返回");
            res.put("waterFee", 0);
            res.put("waterFeeStatus", "IDEMPOTENT");
            res.put("waterFeeFailReason", null);
            res.put("generalAgentCommission", 0);
            // 返回玩家快照（仅做查询展示用，不再调整余额）
            List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, roomId)
                    .eq(RoomPlayer::getIsObserver, false));
            res.put("players", buildPlayerSnapshots(players));
            res.put("idempotent", true);
            log.info("总局结算幂等返回 room={} status={}", room.getRoomNo(), room.getStatus());
            return res;
        }

        long turnover = room.getTotalTurnover() == null ? 0L : room.getTotalTurnover();
        long rake = room.getTotalRake() == null ? 0L : room.getTotalRake();

        // 【公域匹配房】独立结算路径：平台总抽 0.3% + 各代理层级各 0.15% 依次分佣
        if (Boolean.TRUE.equals(room.getIsPublicMatch())) {
            return settlePublicMatchRoom(room, turnover, rake);
        }

        // 1. 退还玩家剩余积分（currentCredits -> user.credits）
        List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsObserver, false));
        for (RoomPlayer rp : players) {
            long left = rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits();
            if (left <= 0) continue;
            User u = userMapper.selectById(rp.getUserId());
            if (u == null) continue;
            long before = u.getCredits() == null ? 0L : u.getCredits();
            u.setCredits(before + left);
            userMapper.updateById(u);

            rp.setLeaveTime(LocalDateTime.now());
            roomPlayerMapper.updateById(rp);

            CreditLog cl = new CreditLog();
            cl.setUserId(u.getId());
            cl.setUsername(u.getUsername());
            cl.setChangeType(3);
            cl.setChangeValue(left);
            cl.setBeforeValue(before);
            cl.setAfterValue(u.getCredits());
            cl.setRoomId(roomId);
            cl.setRoomNo(room.getRoomNo());
            cl.setRemark("房间[" + room.getRoomNo() + "]总局结算，退还剩余筹码:" + left + "，累计输赢:"
                    + (rp.getTotalProfit() == null ? 0L : rp.getTotalProfit()));
            creditLogMapper.insert(cl);
        }

        // 2. 计算多级推广抽成（系统发放信用分，不额外扣房主/玩家）
        //    二级代理开房：二级抽客户1%（房主得），一级抽二级0.5%，总代抽一级0.5%；无一级时总代兜底抽二级1%
        //    一级代理开房：一级兜底抽玩家1.5%（房主得），总代抽一级0.5%
        //    总代理开房：无上级，无抽成
        User owner = userMapper.selectById(room.getOwnerId());
        long ownerCommission = 0L;    // 房主自己作为代理的抽成收益
        long primaryCommission = 0L;  // 一级代理抽成收益
        long gaCommission = 0L;       // 总代理抽成收益
        User primaryAgent = null;
        User generalAgent = null;
        if (owner != null && turnover > 0) {
            if (UserRole.AGENT.getCode().equals(owner.getRole())) {
                ownerCommission = (long) (turnover * platformConfig.getCommissionSecondaryPercent() / 100.0);
                User parent = owner.getParentId() == null ? null : userMapper.selectById(owner.getParentId());
                if (parent != null && UserRole.PRIMARY_AGENT.getCode().equals(parent.getRole())) {
                    primaryAgent = parent;
                    primaryCommission = (long) (turnover * platformConfig.getCommissionPrimaryPercent() / 100.0);
                    User ga = parent.getParentId() == null ? null : userMapper.selectById(parent.getParentId());
                    if (ga != null && UserRole.GENERAL_AGENT.getCode().equals(ga.getRole())) {
                        generalAgent = ga;
                        gaCommission = (long) (turnover * platformConfig.getCommissionGeneralAgentPercent() / 100.0);
                    }
                } else if (parent != null && UserRole.GENERAL_AGENT.getCode().equals(parent.getRole())) {
                    generalAgent = parent;
                    gaCommission = (long) (turnover * platformConfig.getCommissionGeneralAgentFallbackPercent() / 100.0);
                }
            } else if (UserRole.PRIMARY_AGENT.getCode().equals(owner.getRole())) {
                ownerCommission = (long) (turnover * platformConfig.getCommissionPrimaryFallbackPercent() / 100.0);
                User ga = owner.getParentId() == null ? null : userMapper.selectById(owner.getParentId());
                if (ga != null && UserRole.GENERAL_AGENT.getCode().equals(ga.getRole())) {
                    generalAgent = ga;
                    gaCommission = (long) (turnover * platformConfig.getCommissionGeneralAgentPercent() / 100.0);
                }
            }
        }

        // 3. 房主开房抽佣（水费）= 总流水 × 按房主角色比例（总代2%/一级1.5%/二级1%）
        double waterFeePercent = resolveWaterFeePercent(owner);
        long deductedAmount = (long) (turnover * waterFeePercent / 100.0);
        long rebateAmount = ownerCommission; // 房主净成本 = 水费 - 自己的抽成
        long netCost = deductedAmount - rebateAmount;

        long beforeCredit = owner == null ? 0L : (owner.getCredits() == null ? 0L : owner.getCredits());
        long afterCredit = beforeCredit;
        int billStatus = 0;
        String failReason = null;

        if (owner != null && deductedAmount > 0) {
            if (beforeCredit >= deductedAmount) {
                // 扣水费
                afterCredit = beforeCredit - deductedAmount;
                owner.setCredits(afterCredit);
                userMapper.updateById(owner);

                CreditLog cl1 = new CreditLog();
                cl1.setUserId(owner.getId());
                cl1.setUsername(owner.getUsername());
                cl1.setChangeType(5);
                cl1.setChangeValue(-deductedAmount);
                cl1.setBeforeValue(beforeCredit);
                cl1.setAfterValue(afterCredit);
                cl1.setRoomId(roomId);
                cl1.setRoomNo(room.getRoomNo());
                cl1.setRemark("开房抽佣扣除：总流水=" + turnover + " × " + waterFeePercent + "% = " + deductedAmount);
                creditLogMapper.insert(cl1);
            } else {
                // 信用分不足：标记失败，禁止新开房间
                billStatus = 1;
                failReason = "房主信用分不足：当前=" + beforeCredit + "，应扣水费=" + deductedAmount;
                owner.setHasFeeFailure(true);
                userMapper.updateById(owner);
            }
        }

        // 写入返佣账单历史（核心展示表：房间号、扣除多少、系统返还信用分）
        RakeRebateLog rebateLog = new RakeRebateLog();
        rebateLog.setOwnerId(room.getOwnerId());
        rebateLog.setOwnerName(owner == null ? "" : owner.getUsername());
        rebateLog.setOwnerRole(owner == null ? null : owner.getRole());
        rebateLog.setRoomId(roomId);
        rebateLog.setRoomNo(room.getRoomNo());
        rebateLog.setRoomTurnover(turnover);
        rebateLog.setTotalRake(rake);
        rebateLog.setDeductedAmount(deductedAmount);
        rebateLog.setRebateAmount(rebateAmount);
        rebateLog.setNetCost(netCost);
        rebateLog.setBeforeCredit(beforeCredit);
        rebateLog.setAfterCredit(afterCredit);
        rebateLog.setStatus(billStatus);
        rebateLog.setFailReason(failReason);
        rakeRebateLogMapper.insert(rebateLog);

        // 兼容写一条水费日志（水费 = 总流水×水费比例）
        AgentWaterFeeLog feeLog = new AgentWaterFeeLog();
        feeLog.setAgentId(room.getOwnerId());
        feeLog.setAgentName(owner == null ? "" : owner.getUsername());
        feeLog.setRoomId(roomId);
        feeLog.setRoomNo(room.getRoomNo());
        feeLog.setRoomTurnover(turnover);
        feeLog.setFeeAmount(deductedAmount); // 水费 = 扣水费额 (总流水×3%)
        feeLog.setBeforeCredit(beforeCredit);
        feeLog.setAfterCredit(afterCredit);
        feeLog.setStatus(billStatus);
        feeLog.setFailReason(failReason);
        feeLog.setRepaid(billStatus == 0);
        waterFeeLogMapper.insert(feeLog);

        // 更新房间
        room.setTotalWaterFee(deductedAmount);
        room.setStatus(RoomStatus.SETTLED.getCode());
        roomMapper.updateById(room);

        // 4. 发放多级推广抽成（系统发放信用分）
        // 4.1 房主自己作为代理的抽成收益
        if (ownerCommission > 0 && owner != null) {
            long b = owner.getCredits() == null ? 0L : owner.getCredits();
            long a = b + ownerCommission;
            owner.setCredits(a);
            userMapper.updateById(owner);

            CreditLog cl = new CreditLog();
            cl.setUserId(owner.getId());
            cl.setUsername(owner.getUsername());
            cl.setChangeType(11);
            cl.setChangeValue(ownerCommission);
            cl.setBeforeValue(b);
            cl.setAfterValue(a);
            cl.setRoomId(roomId);
            cl.setRoomNo(room.getRoomNo());
            cl.setRemark("代理抽成：房间总流水=" + turnover + "，房主抽成=" + ownerCommission);
            creditLogMapper.insert(cl);
        }
        // 4.2 一级代理抽成
        if (primaryCommission > 0 && primaryAgent != null) {
            long b = primaryAgent.getCredits() == null ? 0L : primaryAgent.getCredits();
            long a = b + primaryCommission;
            primaryAgent.setCredits(a);
            userMapper.updateById(primaryAgent);

            CreditLog cl = new CreditLog();
            cl.setUserId(primaryAgent.getId());
            cl.setUsername(primaryAgent.getUsername());
            cl.setChangeType(6);
            cl.setChangeValue(primaryCommission);
            cl.setBeforeValue(b);
            cl.setAfterValue(a);
            cl.setRoomId(roomId);
            cl.setRoomNo(room.getRoomNo());
            cl.setRemark("一级代理抽成：下级二级[" + (owner != null ? owner.getUsername() : "") + "]房间总流水=" + turnover + " → " + primaryCommission);
            creditLogMapper.insert(cl);
        }
        // 4.3 总代理抽成
        if (gaCommission > 0 && generalAgent != null) {
            long b = generalAgent.getCredits() == null ? 0L : generalAgent.getCredits();
            long a = b + gaCommission;
            generalAgent.setCredits(a);
            userMapper.updateById(generalAgent);

            GeneralAgentCommission gc = new GeneralAgentCommission();
            gc.setGeneralAgentId(generalAgent.getId());
            gc.setGeneralAgentName(generalAgent.getUsername());
            gc.setSourceAgentId(owner != null ? owner.getId() : null);
            gc.setSourceAgentName(owner != null ? owner.getUsername() : "");
            gc.setRoomId(roomId);
            gc.setRoomNo(room.getRoomNo());
            gc.setRoomTurnover(turnover);
            gc.setCommissionAmount(gaCommission);
            gc.setBeforeCredit(b);
            gc.setAfterCredit(a);
            commissionMapper.insert(gc);

            CreditLog cl = new CreditLog();
            cl.setUserId(generalAgent.getId());
            cl.setUsername(generalAgent.getUsername());
            cl.setChangeType(6);
            cl.setChangeValue(gaCommission);
            cl.setBeforeValue(b);
            cl.setAfterValue(a);
            cl.setRoomId(roomId);
            cl.setRoomNo(room.getRoomNo());
            cl.setRemark("总代理抽成：下级[" + (owner != null ? owner.getUsername() : "") + "]房间总流水=" + turnover + " → " + gaCommission);
            creditLogMapper.insert(cl);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("roomNo", room.getRoomNo());
        res.put("totalTurnover", turnover);
        res.put("totalRake", rake);
        // 房主开房抽佣（水费）+ 房主自身抽成收益
        res.put("ownerDeducted", deductedAmount);
        res.put("ownerRebate", rebateAmount);
        res.put("ownerNetCost", netCost);
        res.put("billStatus", billStatus == 0 ? "SUCCESS" : "FAILED");
        res.put("billFailReason", failReason);
        // 兼容旧字段：水费 = 扣水费额 deductedAmount
        res.put("waterFee", deductedAmount);
        res.put("waterFeeStatus", billStatus == 0 ? "SUCCESS" : "FAILED");
        res.put("waterFeeFailReason", failReason);
        res.put("generalAgentCommission", gaCommission);
        res.put("ownerCommission", ownerCommission);
        res.put("primaryCommission", primaryCommission);
        res.put("players", buildPlayerSnapshots(players));

        log.info("总局结算完成(新规则) room={} turnover={} rake={} deduct={} rebate={} netCost={} comm={}",
                room.getRoomNo(), turnover, rake, deductedAmount, rebateAmount, netCost, gaCommission);
        return res;
    }

    /**
     * 结算 Players 快照（原型 23-房间结算页）
     *
     * 原型字段：头像、昵称、账号、带入游戏币、最终游戏币、输赢金额。
     * 原实现仅回传 userId/username，前端无法渲染头像与昵称（文档第十一节 P1 缺口），
     * 此处统一按 userId 补齐 nickname/avatar，三处结算出口（幂等返回 / 常规结算 / 公域结算）共用。
     */
    private List<Map<String, Object>> buildPlayerSnapshots(List<RoomPlayer> players) {
        if (players == null || players.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        // 一次性批量取用户资料，避免 N+1 查询
        List<Long> userIds = players.stream()
                .map(RoomPlayer::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty()
                ? new HashMap<Long, User>()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (RoomPlayer rp : players) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("userId", rp.getUserId());
            m.put("username", rp.getUsername());
            User u = rp.getUserId() == null ? null : userMap.get(rp.getUserId());
            // 昵称：优先取房间内快照，其次取用户表，最后回退账号
            String nickname = rp.getNickname() != null && !rp.getNickname().isEmpty()
                    ? rp.getNickname()
                    : (u != null && u.getNickname() != null ? u.getNickname() : rp.getUsername());
            m.put("nickname", nickname);
            m.put("avatar", u == null ? null : u.getAvatar());
            m.put("isObserver", rp.getIsObserver());
            m.put("seatNo", rp.getSeatNo());
            m.put("buyinCredits", rp.getBuyinCredits());
            m.put("leftCredits", rp.getCurrentCredits());
            m.put("totalProfit", rp.getTotalProfit());
            list.add(m);
        }
        return list;
    }

    /**
     * 公域匹配房结算：
     *   1. 退还玩家剩余筹码（与普通房间一致）
     *   2. 平台总抽 = 总流水 × 0.3%
     *   3. 参与匹配的代理链路（二级 → 一级 → 总代）每级各得 总流水 × 0.15%，依次分佣
     *      —— 以房间绑定的 matchAgentId（首个匹配玩家所属代理）为起点，沿上级链逐级发放
     *   4. 无房主水费（公域房无房主）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settlePublicMatchRoom(GameRoom room, long turnover, long rake) {
        Long roomId = room.getId();

        // 1. 退还玩家剩余筹码
        List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsObserver, false));
        for (RoomPlayer rp : players) {
            long left = rp.getCurrentCredits() == null ? 0L : rp.getCurrentCredits();
            if (left <= 0) continue;
            User u = userMapper.selectById(rp.getUserId());
            if (u == null) continue;
            long before = u.getCredits() == null ? 0L : u.getCredits();
            u.setCredits(before + left);
            userMapper.updateById(u);

            rp.setLeaveTime(LocalDateTime.now());
            roomPlayerMapper.updateById(rp);

            CreditLog cl = new CreditLog();
            cl.setUserId(u.getId());
            cl.setUsername(u.getUsername());
            cl.setChangeType(3);
            cl.setChangeValue(left);
            cl.setBeforeValue(before);
            cl.setAfterValue(u.getCredits());
            cl.setRoomId(roomId);
            cl.setRoomNo(room.getRoomNo());
            cl.setRemark("公域匹配房[" + room.getRoomNo() + "]总局结算，退还剩余筹码:" + left);
            creditLogMapper.insert(cl);
        }

        // 2. 平台总抽 0.3%
        long platformTotalRake = (long) (turnover * platformConfig.getPublicMatchTotalRakePercent() / 100.0);

        // 3. 代理链路各 0.15% 依次分佣（二级 → 一级 → 总代）
        long perAgentCommission = (long) (turnover * platformConfig.getPublicMatchAgentCommissionPercent() / 100.0);
        List<Map<String, Object>> agentCommissions = new ArrayList<>();
        if (perAgentCommission > 0 && room.getMatchAgentId() != null) {
            User cur = userMapper.selectById(room.getMatchAgentId());
            int level = 0;
            while (cur != null && level < 4) {
                if (UserRole.isAgent(cur.getRole())) {
                    long b = cur.getCredits() == null ? 0L : cur.getCredits();
                    long a = b + perAgentCommission;
                    cur.setCredits(a);
                    userMapper.updateById(cur);

                    CreditLog cl = new CreditLog();
                    cl.setUserId(cur.getId());
                    cl.setUsername(cur.getUsername());
                    cl.setChangeType(6);
                    cl.setChangeValue(perAgentCommission);
                    cl.setBeforeValue(b);
                    cl.setAfterValue(a);
                    cl.setRoomId(roomId);
                    cl.setRoomNo(room.getRoomNo());
                    cl.setRemark("公域匹配分佣：房间总流水=" + turnover + " × " + platformConfig.getPublicMatchAgentCommissionPercent() + "% = " + perAgentCommission);
                    creditLogMapper.insert(cl);

                    // 总代理分佣单独入表，便于后台统计
                    if (UserRole.GENERAL_AGENT.getCode().equals(cur.getRole())) {
                        GeneralAgentCommission gc = new GeneralAgentCommission();
                        gc.setGeneralAgentId(cur.getId());
                        gc.setGeneralAgentName(cur.getUsername());
                        gc.setSourceAgentId(room.getMatchAgentId());
                        gc.setSourceAgentName("");
                        gc.setRoomId(roomId);
                        gc.setRoomNo(room.getRoomNo());
                        gc.setRoomTurnover(turnover);
                        gc.setCommissionAmount(perAgentCommission);
                        gc.setBeforeCredit(b);
                        gc.setAfterCredit(a);
                        commissionMapper.insert(gc);
                    }

                    Map<String, Object> item = new HashMap<>();
                    item.put("agentId", cur.getId());
                    item.put("agentName", cur.getUsername());
                    item.put("role", cur.getRole());
                    item.put("commission", perAgentCommission);
                    agentCommissions.add(item);
                }
                cur = cur.getParentId() == null ? null : userMapper.selectById(cur.getParentId());
                level++;
            }
        }

        // 4. 更新房间
        room.setTotalWaterFee(platformTotalRake);
        room.setStatus(RoomStatus.SETTLED.getCode());
        roomMapper.updateById(room);

        Map<String, Object> res = new HashMap<>();
        res.put("roomNo", room.getRoomNo());
        res.put("publicMatch", true);
        res.put("totalTurnover", turnover);
        res.put("totalRake", rake);
        res.put("platformTotalRake", platformTotalRake);
        res.put("platformRakePercent", platformConfig.getPublicMatchTotalRakePercent());
        res.put("perAgentCommission", perAgentCommission);
        res.put("agentCommissionPercent", platformConfig.getPublicMatchAgentCommissionPercent());
        res.put("agentCommissions", agentCommissions);
        res.put("ownerDeducted", 0);
        res.put("ownerRebate", 0);
        res.put("ownerNetCost", 0);
        res.put("billStatus", "SUCCESS");
        res.put("players", buildPlayerSnapshots(players));

        log.info("公域匹配房结算 room={} turnover={} platformRake={} perAgentComm={} agentCount={}",
                room.getRoomNo(), turnover, platformTotalRake, perAgentCommission, agentCommissions.size());
        return res;
    }

    /**
     * 按房主角色返回开房抽佣（水费）比例：总代2% / 一级1.5% / 二级1%，其他默认1%
     */
    private double resolveWaterFeePercent(User owner) {
        if (owner == null) return 1.0;
        if (UserRole.GENERAL_AGENT.getCode().equals(owner.getRole())) {
            return platformConfig.getWaterFeeGeneralAgentPercent();
        }
        if (UserRole.PRIMARY_AGENT.getCode().equals(owner.getRole())) {
            return platformConfig.getWaterFeePrimaryAgentPercent();
        }
        if (UserRole.AGENT.getCode().equals(owner.getRole())) {
            return platformConfig.getWaterFeeSecondaryAgentPercent();
        }
        return 1.0;
    }

    /**
     * 客服补齐代理信用分后，自动补扣欠费，恢复开房权限
     */
    @Transactional(rollbackFor = Exception.class)
    public void repayAgentFeeFailure(Long agentId) {
        User agent = userMapper.selectById(agentId);
        if (agent == null) throw new BizException("代理不存在");
        if (agent.getHasFeeFailure() == null || !agent.getHasFeeFailure()) return;

        List<AgentWaterFeeLog> unpaid = waterFeeLogMapper.selectList(
                new LambdaQueryWrapper<AgentWaterFeeLog>()
                        .eq(AgentWaterFeeLog::getAgentId, agentId)
                        .eq(AgentWaterFeeLog::getStatus, 1)
                        .eq(AgentWaterFeeLog::getRepaid, false)
                        .orderByAsc(AgentWaterFeeLog::getId));
        long remain = agent.getCredits() == null ? 0L : agent.getCredits();
        boolean allRepaid = true;
        for (AgentWaterFeeLog fl : unpaid) {
            long need = fl.getFeeAmount() - (fl.getBeforeCredit() == fl.getAfterCredit() ? fl.getBeforeCredit() - fl.getAfterCredit() + fl.getFeeAmount() : 0);
            // 简化：直接按 feeAmount 补扣
            need = fl.getFeeAmount();
            long before = remain;
            if (before >= need) {
                remain -= need;
                fl.setStatus(0);
                fl.setFailReason(null);
                fl.setRepaid(true);
                fl.setBeforeCredit(before);
                fl.setAfterCredit(remain);
                waterFeeLogMapper.updateById(fl);

                CreditLog cl = new CreditLog();
                cl.setUserId(agentId);
                cl.setUsername(agent.getUsername());
                cl.setChangeType(10);
                cl.setChangeValue(-need);
                cl.setBeforeValue(before);
                cl.setAfterValue(remain);
                cl.setRoomId(fl.getRoomId());
                cl.setRoomNo(fl.getRoomNo());
                cl.setRemark("补扣欠费水费：房间[" + fl.getRoomNo() + "] 金额 " + need);
                creditLogMapper.insert(cl);
            } else {
                allRepaid = false;
            }
        }
        agent.setCredits(remain);
        agent.setHasFeeFailure(!allRepaid);
        userMapper.updateById(agent);
    }
}
