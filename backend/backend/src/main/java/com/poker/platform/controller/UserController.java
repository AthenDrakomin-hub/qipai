package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.entity.AgentWaterFeeLog;
import com.poker.platform.entity.CreditLog;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.GameRound;
import com.poker.platform.entity.RakeRebateLog;
import com.poker.platform.entity.User;
import com.poker.platform.enums.GameType;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.AgentWaterFeeLogMapper;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.GameRoundMapper;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RakeRebateLogMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.CreditService;
import com.poker.platform.service.SettlementService;
import com.poker.platform.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

/**
 * 用户个人中心、代理后台（二级代理+总代理推广中心）
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource 
    private UserMapper userMapper;

    @Resource 
    private CreditLogMapper creditLogMapper;

    @Resource 
    private RakeRebateLogMapper rakeRebateLogMapper;

    @Resource 
    private CreditService creditService;

    @Resource
    private SettlementService settlementService;

    @Resource
    private GameRoundMapper gameRoundMapper;

    @Autowired
    private AgentWaterFeeLogMapper waterFeeLogMapper;

    @Autowired
    private GameRoomMapper gameRoomMapper;

    /** 当前用户信息 */
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) return R.fail("用户不存在");
        Map<String, Object> res = new HashMap<>();
        res.put("id", u.getId());
        res.put("username", u.getUsername());
        res.put("nickname", u.getNickname());
        res.put("role", u.getRole());
        UserRole role = UserRole.fromCode(u.getRole());
        res.put("roleDesc", role == null ? "" : role.getDesc());
        res.put("credits", u.getCredits());
        res.put("inviteCode", u.getInviteCode());
        res.put("parentId", u.getParentId());
        res.put("parentInviteCode", u.getParentInviteCode());
        res.put("avatar", u.getAvatar());
        res.put("hasFeeFailure", u.getHasFeeFailure());
        res.put("status", u.getStatus());
        res.put("lastLoginTime", u.getLastLoginTime());
        return R.ok(res);
    }

    /** 我的积分流水（分页）
     *
     * 原型 19-资金流水页 Tab：全部 / 充值 / 提现 / 佣金 / 赠送 / 对局
     * @param type 变动类型 Tab，可选：RECHARGE / WITHDRAW / COMMISSION / GIFT / GAME；
     *             也可直接传 changeType 数字（如 3 表示对局输赢）。
     *             不传或 ALL 表示全部。
     */
    @GetMapping("/credit-log")
    public R<Page<CreditLog>> creditLog(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "20") Integer size,
                                        @RequestParam(required = false) String type) {
        Long uid = UserContext.getUserId();
        LambdaQueryWrapper<CreditLog> q = new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, uid)
                .orderByDesc(CreditLog::getCreateTime);
        // 按原型 Tab 归组筛选（映射见接口文档 7.5 节 changeType → Tab 对照表）
        List<Integer> types = resolveCreditChangeTypes(type);
        if (types != null && !types.isEmpty()) {
            q.in(CreditLog::getChangeType, types);
        }
        return R.ok(creditLogMapper.selectPage(new Page<>(page, size), q));
    }

    /**
     * 将原型 Tab 名称 / 数字 changeType 解析为 changeType 集合。
     * 返回 null 表示「全部」不过滤。
     *
     * 原型 Tab → changeType 映射（接口文档 7.5）：
     *   充值 1,7,8,13 ｜ 提现 4,5,10 ｜ 佣金 6,11 ｜ 赠送 9,12,14 ｜ 对局 2,3
     */
    private List<Integer> resolveCreditChangeTypes(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        String t = type.trim().toUpperCase();
        if ("ALL".equals(t)) {
            return null;
        }
        if ("RECHARGE".equals(t)) {
            return Arrays.asList(1, 7, 8, 13);
        }
        if ("WITHDRAW".equals(t)) {
            return Arrays.asList(4, 5, 10);
        }
        if ("COMMISSION".equals(t)) {
            return Arrays.asList(6, 11);
        }
        if ("GIFT".equals(t)) {
            return Arrays.asList(9, 12, 14);
        }
        if ("GAME".equals(t)) {
            return Arrays.asList(2, 3);
        }
        // 兼容直接传数字 changeType
        try {
            return Collections.singletonList(Integer.valueOf(t));
        } catch (NumberFormatException e) {
            // 未知 Tab 一律按「全部」处理，避免前端传空串导致 500
            return null;
        }
    }

    // ========== 二级代理：管理下线玩家 ==========

    /** 我的下线玩家列表（含每位下级的总流水） */
    @GetMapping("/agent/subordinates")
    public R<List<Map<String, Object>>> subordinates() {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) {
            throw new BizException("仅代理可访问");
        }
        List<User> list = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, uid)
                .orderByDesc(User::getCreateTime));

        // 为每位下级聚合其作为房主创建的房间总流水
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (User u : list) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("credits", u.getCredits());
            m.put("avatar", u.getAvatar());
            m.put("inviteCode", u.getInviteCode());
            m.put("createTime", u.getCreateTime());
            m.put("status", u.getStatus());
            // 聚合下级作为 owner 创建的房间总流水
            List<GameRoom> rooms = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                    .eq(GameRoom::getOwnerId, u.getId()));
            long totalTurnover = rooms.stream()
                    .filter(r -> r.getTotalTurnover() != null)
                    .mapToLong(GameRoom::getTotalTurnover).sum();
            m.put("totalTurnover", totalTurnover);
            m.put("totalRake", rooms.stream().filter(r -> r.getTotalRake() != null)
                    .mapToLong(GameRoom::getTotalRake).sum());
            m.put("roomCount", rooms.size());
            UserRole r = UserRole.fromCode(u.getRole());
            m.put("roleDesc", r == null ? "" : r.getDesc());
            result.add(m);
        }
        return R.ok(result);
    }

    /** 游戏币调整 DTO（代理/客服/超管共用） */
    public static class AdjustDTO {
        public Long targetUserId;
        /** 变动值（正加负减，不能为0） */
        public Long changeValue;
        /** 操作理由（客服/超管必填；代理选填） */
        public String remark;
    }

    /**
     * 增减游戏币：
     *  - 二级代理：只能操作自己的下级玩家（加值从代理余额扣，减值扣下级，余额需足够）
     *  - 客服 / 超管：可操作任意角色任意用户（虚拟出库；减值需目标余额足够；remark必填）
     */
    @PostMapping("/agent/adjust")
    public R<Long> agentAdjust(@RequestBody AdjustDTO dto) {
        Long uid = UserContext.getUserId();
        String name = UserContext.getUsername();
        Integer role = UserContext.getRole();
        if (role == null) throw new BizException("未登录");
        if (dto.targetUserId == null || dto.changeValue == null || dto.changeValue == 0) {
            throw new BizException("参数无效：targetUserId/changeValue必填且changeValue不能为0");
        }
        User t = userMapper.selectById(dto.targetUserId);
        if (t == null) throw new BizException("目标用户不存在");

        // ------- 客服/超管：虚拟出库，任意目标，理由必填 -------
        if (UserRole.CUSTOMER_SERVICE.getCode().equals(role)
                || UserRole.SUPER_ADMIN.getCode().equals(role)) {
            if (dto.remark == null || dto.remark.trim().isEmpty()) {
                throw new BizException("操作理由为必填项，请详细填写调整原因");
            }
            if (dto.remark.trim().length() < 2) {
                throw new BizException("操作理由至少2个字");
            }
            if (dto.changeValue < 0) {
                long cur = t.getCredits() == null ? 0L : t.getCredits();
                if (cur + dto.changeValue < 0) throw new BizException("目标用户游戏币不足，当前=" + cur);
            }
            String prefix = UserRole.SUPER_ADMIN.getCode().equals(role) ? "[超管调整]" : "[客服调整]";
            String fullRemark = prefix + "(" + name + ")" + dto.remark.trim()
                    + " 变动=" + (dto.changeValue > 0 ? "+" : "") + dto.changeValue;
            Long after = creditService.adminAdjust(dto.targetUserId, dto.changeValue, fullRemark);
            try { settlementService.repayAgentFeeFailure(dto.targetUserId); } catch (Exception ignore) {}
            return R.ok(after);
        }

        // ------- 代理（一级/二级/总代）：只能自己的直接下级 -------
        if (!UserRole.isAgent(role)) throw new BizException("仅代理/客服/超管可操作");
        if (!uid.equals(t.getParentId())) {
            throw new BizException("目标用户不是您的直接下级");
        }
        if (dto.changeValue < 0) {
            // 扣下级游戏币：不能超过下级当前余额
            long cur = t.getCredits() == null ? 0L : t.getCredits();
            if (cur + dto.changeValue < 0) throw new BizException("下级游戏币不足，当前：" + cur);
        } else {
            // 加下级游戏币：从代理游戏币扣
            User me = userMapper.selectById(uid);
            long myCredits = me.getCredits() == null ? 0L : me.getCredits();
            if (myCredits < dto.changeValue) throw new BizException("您的游戏币不足，当前：" + myCredits);
            creditService.changeCredits(uid, -dto.changeValue, 2, null, null, null,
                    uid, name, "向下级[" + t.getUsername() + "]划拨游戏币:" + dto.changeValue
                            + (dto.remark == null ? "" : "，备注:" + dto.remark), false);
        }
        Long after = creditService.agentAdjustSubordinate(uid, name, dto.targetUserId,
                dto.changeValue, dto.remark == null ? "" : dto.remark);
        return R.ok(after);
    }

    /**
     * 代理：9折为直接下级（代理）充值游戏币（需求文档第四章）
     *   —— 下级实收 = 面值 amount；上级实际扣除 = amount × 90%（9折）
     *   —— 仅限直接下级，且下级须为代理角色（一级/二级/总代）
     */
    @PostMapping("/agent/recharge-discount")
    public R<Map<String, Object>> agentRechargeDiscount(@RequestBody AdjustDTO dto) {
        Long uid = UserContext.getUserId();
        String name = UserContext.getUsername();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) throw new BizException("仅代理可操作");
        if (dto.changeValue == null || dto.changeValue <= 0) throw new BizException("充值面值无效");
        User t = userMapper.selectById(dto.targetUserId);
        if (t == null) throw new BizException("目标用户不存在");
        if (!uid.equals(t.getParentId())) throw new BizException("只能为自己的直接下级充值");
        if (!UserRole.isAgent(t.getRole())) throw new BizException("9折充值仅适用于下级代理");

        long faceValue = dto.changeValue;                 // 下级实收面值
        long actualCost = Math.round(faceValue * 0.9);    // 上级实际扣除（9折）
        User me = userMapper.selectById(uid);
        long myCredits = me.getCredits() == null ? 0L : me.getCredits();
        if (myCredits < actualCost) {
            throw new BizException("您的游戏币不足（需扣 " + actualCost + "，9折），当前：" + myCredits);
        }
        // 上级扣 9 折实付
        creditService.changeCredits(uid, -actualCost, 2, null, null, null, uid, name,
                "9折为下级[" + t.getUsername() + "]充值：面值" + faceValue + "，实付" + actualCost, false);
        // 下级收全额面值
        Long after = creditService.agentAdjustSubordinate(uid, name, dto.targetUserId, faceValue,
                (dto.remark == null ? "" : dto.remark + " ") + "(9折充值)");

        Map<String, Object> res = new java.util.HashMap<>();
        res.put("faceValue", faceValue);
        res.put("actualCost", actualCost);
        res.put("subordinateCredits", after);
        return R.ok(res);
    }

    /** 代理：赠送对局中玩家积分（仅自己创建的房间） */
    @PostMapping("/agent/gift")
    public R<Long> agentGift(@RequestBody AdjustDTO dto) {
        Long uid = UserContext.getUserId();
        String name = UserContext.getUsername();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) throw new BizException("仅代理可操作");
        User me = userMapper.selectById(uid);
        long myCredits = me.getCredits() == null ? 0L : me.getCredits();
        if (dto.changeValue == null || dto.changeValue <= 0) throw new BizException("赠送数量无效");
        if (myCredits < dto.changeValue) throw new BizException("您的信用分不足，当前：" + myCredits);
        Long after = creditService.agentGiftPlayer(uid, name, dto.targetUserId, dto.changeValue,
                null, null, dto.remark == null ? "对局赠送" : dto.remark);
        return R.ok(after);
    }

    /**
     * 代理/总代理：水费扣费记录
     * - 一级/二级代理：自己的日志
     * - 总代理：聚合所有下级代理的日志
     */
    @GetMapping("/agent/water-fee-logs")
    public R<List<Map<String, Object>>> agentWaterFeeLogs(@RequestParam(defaultValue = "20") Integer size) {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || (role != 2 && role != 3 && role != 6)) throw new BizException("仅代理/总代理可访问");

        LambdaQueryWrapper<AgentWaterFeeLog> q = new LambdaQueryWrapper<>();
        if (role == 3) {
            // 总代理：聚合下级代理的所有抽水日志
            List<User> subAgents = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getParentId, uid));
            if (subAgents.isEmpty()) {
                return R.ok(java.util.Collections.emptyList());
            }
            java.util.List<Long> ids = subAgents.stream().map(User::getId).collect(Collectors.toList());
            q.in(AgentWaterFeeLog::getAgentId, ids);
        } else {
            q.eq(AgentWaterFeeLog::getAgentId, uid);
        }
        q.orderByDesc(AgentWaterFeeLog::getId);
        q.last("LIMIT " + size);

        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (AgentWaterFeeLog log : waterFeeLogMapper.selectList(q)) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", log.getId());
            m.put("agentId", log.getAgentId());
            m.put("roomNo", log.getRoomNo());
            m.put("roomTurnover", log.getRoomTurnover());
            m.put("feeAmount", log.getFeeAmount());
            m.put("beforeCredit", log.getBeforeCredit());
            m.put("afterCredit", log.getAfterCredit());
            m.put("status", log.getStatus());
            m.put("failReason", log.getFailReason());
            m.put("createTime", log.getCreateTime());
            list.add(m);
        }
        return R.ok(list);
    }

    // ========== 总代理：推广中心 ==========

    /** 总代理：所有下级二级代理 + 玩家数据汇总 */
    @GetMapping("/general-agent/stats")
    public R<Map<String, Object>> generalAgentStats() {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || role != 3) throw new BizException("仅总代理可访问");

        List<User> subAgents = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, uid));
        // 所有玩家（含二级代理下属）
        int totalPlayers = 0;
        long totalSubCredits = 0;
        long subAgentsTotalTurnover = 0;
        long subAgentsTotalRooms = 0;
        for (User sa : subAgents) {
            Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getParentId, sa.getId()));
            totalPlayers += (c == null ? 0 : c.intValue());
            totalSubCredits += sa.getCredits() == null ? 0L : sa.getCredits();
            // 聚合下属代理名下的房间流水与房间数
            List<GameRoom> rooms = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                    .eq(GameRoom::getOwnerId, sa.getId())
                    .eq(GameRoom::getDeleted, 0));
            for (GameRoom r : rooms) {
                subAgentsTotalTurnover += r.getTotalTurnover() == null ? 0L : r.getTotalTurnover();
                subAgentsTotalRooms += 1;
            }
        }
        User me = userMapper.selectById(uid);
        Map<String, Object> res = new HashMap<>();
        res.put("myCredits", me.getCredits());
        res.put("inviteCode", me.getInviteCode());
        res.put("subAgentCount", subAgents.size());
        res.put("totalPlayerCount", totalPlayers);
        res.put("subAgentsTotalCredits", totalSubCredits);
        res.put("subAgentsTotalTurnover", subAgentsTotalTurnover);
        res.put("subAgentsTotalRooms", subAgentsTotalRooms);
        res.put("subAgents", subAgents);
        return R.ok(res);
    }

    // ========== 代理/总代理：我的返佣历史账单 ==========

    /**
     * 当前房主（二级代理/总代理）的返佣账单（房间号、扣除多少、系统返还游戏币）
     * 玩家访问：返回空
     */
    @GetMapping("/rake-rebate-logs")
    public R<Page<RakeRebateLog>> myRakeRebateLogs(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size) {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) {
            // 非房主角色返回空
            return R.ok(new Page<>(page, size));
        }
        LambdaQueryWrapper<RakeRebateLog> q = new LambdaQueryWrapper<RakeRebateLog>()
                .eq(RakeRebateLog::getOwnerId, uid)
                .orderByDesc(RakeRebateLog::getId);
        return R.ok(rakeRebateLogMapper.selectPage(new Page<>(page, size), q));
    }

    // ========== 游戏币：P2P 玩家之间赠送 + 层级划拨 ==========

    /** 根据账号(username)查询用户公开信息（昵称、角色、可用游戏币），用于转账前校验 */
    @GetMapping("/find-by-username")
    public R<Map<String, Object>> findByUsername(@RequestParam String username) {
        if (username == null || username.isEmpty()) throw new BizException("账号不能为空");
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username.trim()));
        if (u == null) return R.fail("目标账号不存在");
        Map<String, Object> res = new HashMap<>();
        res.put("id", u.getId());
        res.put("username", u.getUsername());
        res.put("nickname", u.getNickname());
        res.put("role", u.getRole());
        UserRole ur = UserRole.fromCode(u.getRole());
        res.put("roleDesc", ur == null ? "" : ur.getDesc());
        // 不返回精确余额，仅返回是否有冻结等状态
        res.put("status", u.getStatus());
        return R.ok(res);
    }

    /** 玩家之间赠送游戏币（P2P，type=12）— 仅玩家角色可用 */
    public static class TransferDTO {
        /** 接收方账号(username) — 兼容 toUsername / targetUsername */
        public String toUsername;
        public String targetUsername;
        /** 赠送数量（>0）— 兼容 value / changeValue */
        public Long value;
        public Long changeValue;
        public String remark;

        public String resolvedToUsername() {
            return (toUsername != null && !toUsername.isEmpty()) ? toUsername : targetUsername;
        }
        public Long resolvedValue() {
            return value != null ? value : changeValue;
        }
    }

    @PostMapping("/transfer/player-to-player")
    public R<Long> playerToPlayer(@RequestBody TransferDTO dto) {
        Long fromId = UserContext.getUserId();
        String fromName = UserContext.getUsername();
        Integer fromRole = UserContext.getRole();
        if (fromRole == null || !UserRole.PLAYER.getCode().equals(fromRole)) {
            throw new BizException("仅玩家可使用赠送游戏币（P2P）功能");
        }
        String toName = dto == null ? null : dto.resolvedToUsername();
        Long val = dto == null ? null : dto.resolvedValue();
        if (toName == null || toName.isEmpty()) {
            throw new BizException("请输入接收方账号");
        }
        if (val == null || val <= 0) {
            throw new BizException("赠送数量必须大于0");
        }
        // 不允许给自己转账
        if (toName.trim().equalsIgnoreCase(fromName)) {
            throw new BizException("不能向自己赠送游戏币");
        }
        // 查接收方
        User to = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, toName.trim()));
        if (to == null) throw new BizException("接收方账号不存在");
        if (!UserRole.PLAYER.getCode().equals(to.getRole())) {
            throw new BizException("P2P赠送仅支持玩家→玩家，接收方不是玩家");
        }
        if (to.getStatus() != null && to.getStatus() == 1) {
            throw new BizException("接收方账号已被冻结");
        }
        long after = creditService.playerTransfer(fromId, fromName, to.getId(), to.getUsername(),
                val, dto.remark);
        return R.ok(after);
    }

    /**
     * 层级划拨（推广发放游戏币，type=13）
     * 权限：
     *   - 二级代理(2) → 自己的下级玩家(parentId == me)，扣自身游戏币
     *   - 总代理(3) → 自己的下级二级代理(parentId == me)，扣自身游戏币
     *   - 客服(4) → 玩家/代理/总代理 均可（不允许发给另一个客服/超管），虚拟出库不扣自身
     *   - 超管(5) → 任意角色均可，虚拟出库不扣自身
     */
    @PostMapping("/transfer/promote-grant")
    public R<Long> promoteGrant(@RequestBody TransferDTO dto) {
        Long fromId = UserContext.getUserId();
        String fromName = UserContext.getUsername();
        Integer fromRole = UserContext.getRole();
        if (fromRole == null) throw new BizException("未登录");

        String toName = dto == null ? null : dto.resolvedToUsername();
        Long val = dto == null ? null : dto.resolvedValue();
        if (toName == null || toName.isEmpty()) {
            throw new BizException("请输入接收方账号");
        }
        if (val == null || val <= 0) {
            throw new BizException("划拨数量必须大于0");
        }
        if (toName.trim().equalsIgnoreCase(fromName)) {
            throw new BizException("不能向自己划拨游戏币");
        }

        User to = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, toName.trim()));
        if (to == null) throw new BizException("接收方账号不存在");
        if (to.getStatus() != null && to.getStatus() == 1) {
            throw new BizException("接收方账号已被冻结");
        }
        Integer toRole = to.getRole();

        // 角色权限校验
        boolean deductFrom;  // 是否扣自身游戏币
        if (UserRole.AGENT.getCode().equals(fromRole)) {
            // 二级代理 → 下级玩家
            if (!UserRole.PLAYER.getCode().equals(toRole)) {
                throw new BizException("二级代理仅能向下级玩家划拨游戏币");
            }
            if (!fromId.equals(to.getParentId())) {
                throw new BizException("目标玩家不是您的下级玩家，请核对上级归属");
            }
            deductFrom = true;
        } else if (UserRole.PRIMARY_AGENT.getCode().equals(fromRole)) {
            // 一级代理 → 下级二级代理
            if (!UserRole.AGENT.getCode().equals(toRole)) {
                throw new BizException("一级代理仅能向下级二级代理划拨游戏币");
            }
            if (!fromId.equals(to.getParentId())) {
                throw new BizException("目标二级代理不是您的下级代理，请核对上级归属");
            }
            deductFrom = true;
        } else if (UserRole.GENERAL_AGENT.getCode().equals(fromRole)) {
            // 总代理 → 下级一级代理
            if (!UserRole.PRIMARY_AGENT.getCode().equals(toRole)) {
                throw new BizException("总代理仅能向下级一级代理划拨游戏币");
            }
            if (!fromId.equals(to.getParentId())) {
                throw new BizException("目标一级代理不是您的下级代理，请核对上级归属");
            }
            deductFrom = true;
        } else if (UserRole.CUSTOMER_SERVICE.getCode().equals(fromRole)) {
            // 客服：可发给玩家/代理/总代理，不可发给客服/超管
            if (UserRole.CUSTOMER_SERVICE.getCode().equals(toRole)
                    || UserRole.SUPER_ADMIN.getCode().equals(toRole)) {
                throw new BizException("客服不能向客服或超管划拨游戏币");
            }
            deductFrom = false;  // 虚拟出库
        } else if (UserRole.SUPER_ADMIN.getCode().equals(fromRole)) {
            // 超管：发给任意角色（包括客服、超管自己？不允许发自己，已被上方dto.toUsername判断拦截）
            deductFrom = false;  // 超管虚拟出库
        } else {
            // 玩家：不允许调用此接口（玩家走 player-to-player）
            throw new BizException("您没有层级划拨权限，玩家请使用赠送功能");
        }

        long after = creditService.promoteGrant(fromId, fromName, to.getId(), to.getUsername(),
                fromRole, val, dto.remark, deductFrom);
        return R.ok(after);
    }

    // ==================== 个人中心：头像/昵称、修改密码、历史战绩 ====================

    /** 更新头像和/或昵称 */
    @PutMapping("/profile")
    public R<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) return R.fail("用户不存在");
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");
        if (nickname != null) {
            String n = nickname.trim();
            if (n.isEmpty()) throw new BizException("昵称不能为空");
            if (n.length() > 20) throw new BizException("昵称不能超过20个字符");
            u.setNickname(n);
        }
        if (avatar != null) {
            u.setAvatar(avatar);
        }
        userMapper.updateById(u);
        Map<String, Object> res = new HashMap<>();
        res.put("nickname", u.getNickname());
        res.put("avatar", u.getAvatar());
        return R.ok(res);
    }

    /** 修改密码 */
    @PostMapping("/change-password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) return R.fail("用户不存在");
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || oldPwd.isEmpty()) throw new BizException("请输入原密码");
        if (newPwd == null || newPwd.length() < 6) throw new BizException("新密码至少6位");
        String expected = JwtUtil.encryptPassword(oldPwd, u.getSalt());
        if (!expected.equals(u.getPassword())) throw new BizException("原密码错误");
        u.setPassword(JwtUtil.encryptPassword(newPwd, u.getSalt()));
        userMapper.updateById(u);
        return R.ok(null);
    }

    /** 我的历史战绩（分页） */
    @GetMapping("/game-history")
    public R<Page<Map<String, Object>>> gameHistory(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size,
                                                    @RequestParam(required = false) String gameType) {
        Long uid = UserContext.getUserId();
        String uidStr = String.valueOf(uid);
        Page<GameRound> p = new Page<>(page, size);
        // winnerIds 或 loserIds 包含当前用户的记录
        Page<GameRound> result = gameRoundMapper.selectPage(p, new LambdaQueryWrapper<GameRound>()
                .like(GameRound::getWinnerIds, uidStr)
                .or()
                .like(GameRound::getLoserIds, uidStr)
                .orderByDesc(GameRound::getId));

        // 房间缓存：GameRound 不冗余 gameType，需按 roomId 反查房间取得玩法（原型 18 Tab 筛选依赖）
        List<GameRound> records = result.getRecords();
        Map<Long, GameRoom> roomMap = new HashMap<>();
        List<Long> roomIds = records.stream()
                .map(GameRound::getRoomId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!roomIds.isEmpty()) {
            for (GameRoom r : gameRoomMapper.selectBatchIds(roomIds)) {
                roomMap.put(r.getId(), r);
            }
        }

        // 原型 18 Tab：全部 / 德州(TEXAS) / 金花(JINHUA) / 三公(SANGONG) / 牛牛(DOUNIU)
        // 「三公」需同时命中 SANGONG 与 TONGBI_SANGONG；「牛牛」同时命中 DOUNIU 与 TONGBI_NIUNIU
        final List<String> wanted = resolveGameTypeFilter(gameType);

        Page<Map<String, Object>> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(records.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("roomNo", r.getRoomNo());
                    m.put("roundNo", r.getRoundNo());
                    m.put("turnover", r.getRoundTurnover());
                    m.put("rake", r.getRoundRake());
                    m.put("createTime", r.getCreateTime());
                    GameRoom room = r.getRoomId() == null ? null : roomMap.get(r.getRoomId());
                    m.put("gameType", room == null ? null : room.getGameType());
                    m.put("gameTypeDesc", room == null ? null : GameType.descOf(room.getGameType()));
                    m.put("roomLevel", room == null ? null : room.getRoomLevel());
                    // 判断输赢
                    boolean win = r.getWinnerIds() != null && r.getWinnerIds().contains(uidStr);
                    m.put("win", win);
                    return m;
                })
                .filter(m -> wanted == null || wanted.contains(m.get("gameType")))
                .collect(Collectors.toList()));
        return R.ok(vo);
    }

    /**
     * 解析战绩 Tab 为 gameType 集合；返回 null 表示「全部」。
     * 兼容前端可能直接传具体枚举 code（TEXAS/JINHUA/SANGONG/DOUNIU/TONGBI_*）。
     */
    private List<String> resolveGameTypeFilter(String gameType) {
        if (gameType == null || gameType.trim().isEmpty()) {
            return null;
        }
        String t = gameType.trim().toUpperCase();
        if ("ALL".equals(t)) {
            return null;
        }
        if ("SANGONG".equals(t)) {
            return Arrays.asList("SANGONG", "TONGBI_SANGONG");
        }
        if ("DOUNIU".equals(t)) {
            return Arrays.asList("DOUNIU", "TONGBI_NIUNIU");
        }
        return Collections.singletonList(t);
    }

    /** 生成测试对局数据（临时接口） */
    @PostMapping("/test-gen-rounds")
    public R<Void> testGenRounds() {
        Long uid = UserContext.getUserId(); // player1 的 id
        String uidStr = String.valueOf(uid);
        // 找一些其他用户 id 做对手
        List<User> others = userMapper.selectList(new LambdaQueryWrapper<User>()
                .ne(User::getId, uid).last("limit 5"));
        String[] roomNos = {"R001", "R002", "R003", "R004", "R005"};
        String[] gameTypes = {"德州竞技", "金花竞技", "抢庄牛", "通比牛", "抢庄三公"};
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 20; i++) {
            GameRound r = new GameRound();
            r.setRoomId((long) (100 + i % 5));
            r.setRoomNo(roomNos[i % roomNos.length]);
            r.setRoundNo(i / 5 + 1);
            long turnover = 50 + rnd.nextInt(450); // 50~500
            long rake = (long)(turnover * 0.03 * 10) / 10;
            r.setRoundTurnover(turnover);
            r.setRoundRake(rake);
            r.setBankerId(uid);
            boolean win = rnd.nextBoolean();
            if (win) {
                r.setWinnerIds(uidStr);
                r.setLoserIds(others.isEmpty() ? "1" : others.get(0).getId().toString());
            } else {
                r.setWinnerIds(others.isEmpty() ? "1" : others.get(0).getId().toString());
                r.setLoserIds(uidStr);
            }
            r.setCreateTime(LocalDateTime.now().minusHours(i * 3L));
            gameRoundMapper.insert(r);
        }
        return R.ok(null);
    }

    /** 生成测试游戏币流水（临时接口） */
    @PostMapping("/test-gen-credit-logs")
    public R<Void> testGenCreditLogs() {
        Long uid = UserContext.getUserId();
        String username = UserContext.getUsername();
        java.util.Random rnd = new java.util.Random();
        long balance = 5000;
        int[] types = {1, 2, 3, 4, 5, 7, 14};
        String[] remarks = {"注册赠送", "游戏输赢", "抽水扣除", "反佣奖励", "代理赠予", "客服增加", "玩家转赠"};
        for (int i = 0; i < 20; i++) {
            int typeIdx = rnd.nextInt(types.length);
            int type = types[typeIdx];
            long change;
            if (type == 1) change = 1000; // 注册赠送
            else if (type == 3) change = -(rnd.nextInt(50) + 10); // 抽水
            else if (type == 7) change = rnd.nextInt(500) + 100; // 客服增加
            else if (type == 14) change = rnd.nextInt(200) + 50; // 玩家转赠
            else change = rnd.nextInt(300) + 50; // 输赢/反佣/赠予
            if (type == 2 && rnd.nextBoolean()) change = -change; // 输赢有负
            balance += change;
            CreditLog log = new CreditLog();
            log.setUserId(uid);
            log.setUsername(username);
            log.setChangeType(type);
            log.setChangeValue(change);
            log.setBeforeValue(balance - change);
            log.setAfterValue(balance);
            log.setRemark(remarks[typeIdx]);
            log.setCreateTime(LocalDateTime.now().minusHours(i * 5L));
            creditLogMapper.insert(log);
        }
        return R.ok(null);
    }
}
