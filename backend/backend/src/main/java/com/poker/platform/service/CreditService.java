package com.poker.platform.service;

import com.poker.platform.entity.CreditLog;
import com.poker.platform.entity.User;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 游戏币 核心服务（1:1 统一等值；玩家/代理/总代/客服通用字段）
 */
@Service
public class CreditService {

    private static final Logger log = LoggerFactory.getLogger(CreditService.class);

    @Resource
    private UserMapper userMapper;
    @Resource 
    private CreditLogMapper creditLogMapper;

    /**
     * 变动游戏币（统一入口，保证流水+余额原子性）
     *
     * @param userId       目标用户
     * @param changeValue  变动值（正加负减）
     * @param changeType   变动类型
     * @param roomId       关联房间（可空）
     * @param roomNo       房间号
     * @param roundId      关联对局（可空）
     * @param operatorId   操作人（可空）
     * @param operatorName 操作人名称
     * @param remark       备注
     * @param allowNegative 是否允许余额为负
     * @return 变动后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public Long changeCredits(Long userId, long changeValue, int changeType,
                              Long roomId, String roomNo, Long roundId,
                              Long operatorId, String operatorName,
                              String remark, boolean allowNegative) {
        if (changeValue == 0) return null;

        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException("用户不存在");

        long before = user.getCredits() == null ? 0L : user.getCredits();
        long after = before + changeValue;

        if (!allowNegative && after < 0) {
            throw new BizException("游戏币不足：需要 " + Math.abs(changeValue) + "，当前 " + before);
        }

        user.setCredits(after);
        userMapper.updateById(user);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setUsername(user.getUsername());
        log.setChangeType(changeType);
        log.setChangeValue(changeValue);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setRoomId(roomId);
        log.setRoomNo(roomNo);
        log.setRoundId(roundId);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setRemark(remark);
        creditLogMapper.insert(log);

        return after;
    }

    /**
     * 便捷方法：客服/管理员人工调整游戏币
     */
    @Transactional(rollbackFor = Exception.class)
    public Long adminAdjust(Long targetUserId, long changeValue, String remark) {
        Long operatorId = UserContext.getUserId();
        String operatorName = UserContext.getUsername();
        Integer opRole = UserContext.getRole();
        // changeType: 7=客服 8=超管
        int type = (opRole != null && opRole == 5) ? 8 : 7;
        return changeCredits(targetUserId, changeValue, type, null, null, null,
                operatorId, operatorName, remark, true);
    }

    /**
     * 代理增减下线游戏币（type=2）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long agentAdjustSubordinate(Long agentId, String agentName,
                                       Long playerId, long changeValue, String remark) {
        return changeCredits(playerId, changeValue, 2, null, null, null,
                agentId, agentName, remark, true);
    }

    /**
     * 代理赠送对局中玩家游戏币（type=9）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long agentGiftPlayer(Long agentId, String agentName,
                                Long playerId, long value, Long roomId, String roomNo, String remark) {
        // 先扣代理游戏币
        long agentAfter = changeCredits(agentId, -value, 9, roomId, roomNo, null,
                agentId, agentName, "赠送玩家[" + playerId + "]游戏币:" + value + "，" + remark, false);
        // 再加玩家
        long playerAfter = changeCredits(playerId, value, 9, roomId, roomNo, null,
                agentId, agentName, "代理赠送游戏币:" + value + "，" + remark, true);
        return playerAfter;
    }

    /**
     * 玩家之间赠送游戏币（P2P 转账，type=12）
     */
    @Transactional(rollbackFor = Exception.class)
    public long playerTransfer(Long fromId, String fromName,
                               Long toId, String toName, long value, String remark) {
        if (value <= 0) throw new BizException("赠送数量必须大于0");
        // 先扣转出方（不允许负）
        changeCredits(fromId, -value, 12, null, null, null,
                fromId, fromName, "向玩家[" + toName + "]赠送游戏币:" + value
                        + (remark == null ? "" : "，备注:" + remark), false);
        // 再加转入方
        long after = changeCredits(toId, value, 12, null, null, null,
                fromId, fromName, "收到玩家[" + fromName + "]赠送游戏币:" + value
                        + (remark == null ? "" : "，备注:" + remark), true);
        log.info("P2P转账 from={} to={} value={}", fromName, toName, value);
        return after;
    }

    /**
     * 推广划拨（层级发放游戏币，type=13）
     *  —— 总代理 → 自己的下级二级代理
     *  —— 二级代理 → 自己的下级玩家
     *  —— 客服后台 → 总代理 / 代理 / 玩家（均可）
     *  —— 超管 → 任意用户（均可）
     *
     * @param deductFrom 是否真实扣除转出方游戏币（客服/超管=虚拟出库，不扣自身，false）
     */
    @Transactional(rollbackFor = Exception.class)
    public long promoteGrant(Long fromId, String fromName,
                             Long toId, String toName, Integer fromRole,
                             long value, String remark, boolean deductFrom) {
        if (value <= 0) throw new BizException("划拨数量必须大于0");
        if (deductFrom) {
            // 真实扣转出方（代理/总代），不允许负
            changeCredits(fromId, -value, 13, null, null, null,
                    fromId, fromName, "向" + roleShort(toRoleOf(toId)) + "[" + toName + "]划拨游戏币:" + value
                            + (remark == null ? "" : "，备注:" + remark), false);
        } else {
            // 虚拟出库（客服/超管），仅记一条转出流水（remark表达去向），余额不变
            CreditLog outLog = new CreditLog();
            User from = userMapper.selectById(fromId);
            long cur = from.getCredits() == null ? 0L : from.getCredits();
            outLog.setUserId(fromId);
            outLog.setUsername(fromName);
            outLog.setChangeType(13);
            outLog.setChangeValue(-value);  // 显示为出账
            outLog.setBeforeValue(cur);
            outLog.setAfterValue(cur);      // 余额不变
            outLog.setOperatorId(fromId);
            outLog.setOperatorName(fromName);
            outLog.setRemark("向" + roleShort(toRoleOf(toId)) + "[" + toName + "]发放游戏币:" + value
                    + "（虚拟出库，不扣自身余额）"
                    + (remark == null ? "" : "，备注:" + remark));
            creditLogMapper.insert(outLog);
        }
        // 给转入方加
        long after = changeCredits(toId, value, 13, null, null, null,
                fromId, fromName, "收到" + roleShort(fromRole) + "[" + fromName + "]发放游戏币:" + value
                        + (remark == null ? "" : "，备注:" + remark), true);
        log.info("推广划拨 from={}({}) deduct={} to={} value={}", fromName, fromRole, deductFrom, toName, value);
        return after;
    }

    /** 查某用户角色，用于流水备注的"玩家/代理/总代"简称 */
    private Integer toRoleOf(Long userId) {
        if (userId == null) return null;
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getRole();
    }

    private static String roleShort(Integer roleCode) {
        if (roleCode == null) return "用户";
        switch (roleCode) {
            case 1: return "玩家";
            case 2: return "二级代理";
            case 3: return "总代理";
            case 4: return "客服";
            case 5: return "超管";
            case 6: return "一级代理";
            default: return "用户";
        }
    }
}
