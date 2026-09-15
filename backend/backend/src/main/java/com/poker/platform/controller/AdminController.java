package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.entity.*;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.*;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.CreditService;
import com.poker.platform.service.SettlementService;
import com.poker.platform.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客服后台 + 超级管理后台 控制器
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired private UserMapper userMapper;
    @Autowired private CreditLogMapper creditLogMapper;
    @Autowired private AgentWaterFeeLogMapper waterFeeLogMapper;
    @Autowired private GeneralAgentCommissionMapper commissionMapper;
    @Autowired private RakeRebateLogMapper rakeRebateLogMapper;
    @Autowired private GameRoomMapper roomMapper;
    @Autowired private GameRoundMapper roundMapper;
    @Autowired private PlatformAnnouncementMapper annMapper;
    @Autowired private PlatformComplaintMapper complaintMapper;
    @Autowired private CreditService creditService;
    @Autowired private SettlementService settlementService;

    // ============ 权限校验工具 ============
    private void requireRole(Integer minRole) {
        Integer role = UserContext.getRole();
        if (role == null || role < minRole) {
            throw new BizException("无权限访问该接口");
        }
    }

    // ========== 账号管理（客服/超管均可，超管可改角色/密码） ==========

    /** 账号分页查询 */
    @GetMapping("/users")
    public R<Page<User>> users(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer role,
                               @RequestParam(required = false) Integer status,
                               @RequestParam(required = false) Long parentId) {
        requireRole(4);
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            q.and(x -> x.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getInviteCode, keyword));
        }
        if (role != null) q.eq(User::getRole, role);
        if (status != null) q.eq(User::getStatus, status);
        if (parentId != null) q.eq(User::getParentId, parentId);
        q.orderByDesc(User::getCreateTime);
        Page<User> resultPage = userMapper.selectPage(new Page<>(page, size), q);
        // 填充上级用户名
        if (resultPage.getRecords() != null && !resultPage.getRecords().isEmpty()) {
            Set<Long> parentIds = resultPage.getRecords().stream()
                    .map(User::getParentId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!parentIds.isEmpty()) {
                Map<Long, String> parentNameMap = userMapper.selectBatchIds(parentIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
                resultPage.getRecords().forEach(u -> {
                    if (u.getParentId() != null) {
                        u.setParentUsername(parentNameMap.get(u.getParentId()));
                    }
                });
            }
        }
        return R.ok(resultPage);
    }

    /** 账号详情（含上级/下级信息） */
    @GetMapping("/users/{id}")
    public R<Map<String, Object>> userDetail(@PathVariable Long id) {
        requireRole(4);
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        Map<String, Object> res = new HashMap<>();
        res.put("user", u);
        if (u.getParentId() != null) res.put("parent", userMapper.selectById(u.getParentId()));
        List<User> subs = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, id));
        res.put("subordinates", subs);
        return R.ok(res);
    }

    /** 创建账号（超管直接创建，不通过邀请码流程） */
    public static class CreateUserDTO {
        public String username;
        public String password;
        public String securityCode;
        public String nickname;
        public Integer role;
        public Long credits;
        public Long parentId;
        public String inviteCode;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSecurityCode() { return securityCode; }
        public void setSecurityCode(String securityCode) { this.securityCode = securityCode; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Integer getRole() { return role; }
        public void setRole(Integer role) { this.role = role; }
        public Long getCredits() { return credits; }
        public void setCredits(Long credits) { this.credits = credits; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    }

    @PostMapping("/users")
    public R<User> createUser(@RequestBody CreateUserDTO dto) {
        requireRole(5); // 仅超管
        if (dto.username == null || dto.password == null) throw new BizException("账号和密码必填");
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username));
        if (c != null && c > 0) throw new BizException("账号已存在");
        User u = new User();
        u.setUsername(dto.username);
        String salt = JwtUtil.generateSalt();
        u.setSalt(salt);
        u.setPassword(JwtUtil.encryptPassword(dto.password, salt));
        // 安全码独立加密存储，不再明文；未指定时用默认 888888
        u.setSecurityCode(JwtUtil.encryptPassword(dto.securityCode == null ? "888888" : dto.securityCode, salt));
        u.setNickname(dto.nickname == null ? dto.username : dto.nickname);
        u.setRole(dto.role == null ? UserRole.PLAYER.getCode() : dto.role);
        u.setCredits(dto.credits == null ? 0L : dto.credits);
        u.setStatus(0);
        u.setHasFeeFailure(false);
        // 邀请码
        if (dto.inviteCode != null && !dto.inviteCode.isEmpty()) {
            u.setInviteCode(dto.inviteCode);
        } else {
            String code; int retry = 0;
            do {
                code = JwtUtil.generateInviteCode();
                Long cc = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getInviteCode, code));
                if (cc == null || cc == 0) break;
                retry++;
            } while (retry < 20);
            u.setInviteCode(code);
        }
        if (dto.parentId != null) {
            User p = userMapper.selectById(dto.parentId);
            if (p != null) { u.setParentId(p.getId()); u.setParentInviteCode(p.getInviteCode()); }
        }
        userMapper.insert(u);
        return R.ok(u);
    }

    /** 修改账号 */
    @PutMapping("/users/{id}")
    public R<User> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(5);
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if (body.containsKey("nickname")) u.setNickname(String.valueOf(body.get("nickname")));
        if (body.containsKey("role")) u.setRole(Integer.valueOf(String.valueOf(body.get("role"))));
        if (body.containsKey("status")) u.setStatus(Integer.valueOf(String.valueOf(body.get("status"))));
        if (body.containsKey("password")) {
            String salt = JwtUtil.generateSalt();
            u.setSalt(salt);
            u.setPassword(JwtUtil.encryptPassword(String.valueOf(body.get("password")), salt));
        }
        if (body.containsKey("parentId")) {
            Object pidObj = body.get("parentId");
            if (pidObj != null && !"".equals(pidObj) && !"null".equals(String.valueOf(pidObj))) {
                Long pid = Long.valueOf(String.valueOf(pidObj));
                u.setParentId(pid);
                User p = userMapper.selectById(pid);
                if (p != null) u.setParentInviteCode(p.getInviteCode());
            } else {
                u.setParentId(null);
                u.setParentInviteCode(null);
            }
        }
        userMapper.updateById(u);
        return R.ok(u);
    }

    @DeleteMapping("/users/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        requireRole(5);
        userMapper.deleteById(id);
        return R.ok();
    }

    // ========== 超管：玩家晋升代理 / 总代理 ==========

    public static class PromoteDTO {
        /** 目标角色：2=二级代理 3=总代理 */
        public Integer targetRole;
        /** 上级用户ID（选填；升小代时通常=总代理ID） */
        public Long parentId;
        /** 初始信用分（选填，默认0） */
        public Long initCredits;

        public Integer getTargetRole() { return targetRole; }
        public void setTargetRole(Integer targetRole) { this.targetRole = targetRole; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public Long getInitCredits() { return initCredits; }
        public void setInitCredits(Long initCredits) { this.initCredits = initCredits; }
    }

    /**
     * 超级管理员授权晋升：将玩家晋升为二级代理或总代理
     * - 仅超管可操作
     * - 目标用户必须是玩家(role=1)
     * - 晋升总代理：parentId可不填；若填写则必须是超管/已存在总代（上级链正确）
     * - 晋升小代：parentId可选（通常=总代理ID，便于推广链归属）
     */
    @PostMapping("/users/{id}/promote")
    public R<User> promoteUser(@PathVariable Long id, @RequestBody PromoteDTO dto) {
        requireRole(5);
        if (dto == null || dto.targetRole == null || !UserRole.isAgent(dto.targetRole)) {
            throw new BizException("晋升目标角色无效（需指定2=二级代理/6=一级代理/3=总代理）");
        }
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if (!UserRole.PLAYER.getCode().equals(u.getRole())) {
            throw new BizException("仅玩家角色可被授权晋升，当前角色=" + u.getRole());
        }
        // 校验上级
        User parent = null;
        if (dto.parentId != null) {
            parent = userMapper.selectById(dto.parentId);
            if (parent == null) throw new BizException("上级用户不存在");
            Integer pr = parent.getRole();
            if (UserRole.GENERAL_AGENT.getCode().equals(dto.targetRole)) {
                // 升总代：上级必须是超管
                if (!UserRole.SUPER_ADMIN.getCode().equals(pr)) {
                    throw new BizException("总代理的上级必须是超级管理员");
                }
            } else if (UserRole.PRIMARY_AGENT.getCode().equals(dto.targetRole)) {
                // 升一级代理：上级必须是总代理
                if (!UserRole.GENERAL_AGENT.getCode().equals(pr)) {
                    throw new BizException("一级代理的上级必须是总代理");
                }
            } else {
                // 升二级代理：上级必须是一级代理
                if (!UserRole.PRIMARY_AGENT.getCode().equals(pr)) {
                    throw new BizException("二级代理的上级必须是一级代理");
                }
            }
        } else if (UserRole.GENERAL_AGENT.getCode().equals(dto.targetRole)) {
            // 升总代 没指定上级 → 归属当前超管
            parent = userMapper.selectById(UserContext.getUserId());
        }

        u.setRole(dto.targetRole);
        if (parent != null) {
            u.setParentId(parent.getId());
            u.setParentInviteCode(parent.getInviteCode());
        }
        long credits = dto.initCredits == null ? 0L : dto.initCredits;
        if (credits > 0) {
            long before = u.getCredits() == null ? 0L : u.getCredits();
            u.setCredits(before + credits);
            // 记流水
            CreditLog cl = new CreditLog();
            cl.setUserId(u.getId());
            cl.setUsername(u.getUsername());
            cl.setChangeType(1);
            cl.setChangeValue(credits);
            cl.setBeforeValue(before);
            cl.setAfterValue(u.getCredits());
            cl.setOperatorId(UserContext.getUserId());
            cl.setOperatorName(UserContext.getUsername());
            String roleName = UserRole.GENERAL_AGENT.getCode().equals(dto.targetRole) ? "总代理"
                    : (UserRole.PRIMARY_AGENT.getCode().equals(dto.targetRole) ? "一级代理" : "二级代理");
            cl.setRemark("超管[" + UserContext.getUsername() + "]授权晋升为" + roleName + "，发放初始信用分" + credits);
            creditLogMapper.insert(cl);
        }
        u.setHasFeeFailure(false); // 晋升成功后重置扣费失败标记
        userMapper.updateById(u);

        log.info("用户晋升成功: userId={} username={} targetRole={} parentId={}",
                u.getId(), u.getUsername(), dto.targetRole,
                parent == null ? null : parent.getId());
        return R.ok(u);
    }

    // ========== 返佣历史账单（超管全量 / 客服可查）==========

    @GetMapping("/rake-rebate-logs")
    public R<Page<RakeRebateLog>> rakeRebateLogs(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "20") Integer size,
                                                 @RequestParam(required = false) Long ownerId,
                                                 @RequestParam(required = false) String roomNo,
                                                 @RequestParam(required = false) Integer ownerRole,
                                                 @RequestParam(required = false) Integer status) {
        requireRole(4);
        LambdaQueryWrapper<RakeRebateLog> q = new LambdaQueryWrapper<>();
        if (ownerId != null) q.eq(RakeRebateLog::getOwnerId, ownerId);
        if (ownerRole != null) q.eq(RakeRebateLog::getOwnerRole, ownerRole);
        if (roomNo != null && !roomNo.isEmpty()) q.eq(RakeRebateLog::getRoomNo, roomNo);
        if (status != null) q.eq(RakeRebateLog::getStatus, status);
        q.orderByDesc(RakeRebateLog::getId);
        return R.ok(rakeRebateLogMapper.selectPage(new Page<>(page, size), q));
    }

    // ========== 游戏币人工调整（客服/超管）==========

    public static class CreditAdjustDTO {
        /** 目标用户ID（兼容字段名 userId / targetUserId） */
        public Long userId;
        public Long targetUserId;
        /** 正加负减 */
        public Long changeValue;
        /** 操作理由（必填） */
        public String remark;

        public Long resolvedUserId() {
            return userId != null ? userId : targetUserId;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getTargetUserId() { return targetUserId; }
        public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
        public Long getChangeValue() { return changeValue; }
        public void setChangeValue(Long changeValue) { this.changeValue = changeValue; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /**
     * 客服 / 超管：直接对任意用户增减游戏币
     * —— 虚拟出库，不从操作者账户扣，必须填写操作理由（remark 必填）
     */
    @PostMapping({"/credit/adjust", "/users/credit/adjust", "/users/credit-adjust", "/credit-adjust"})
    public R<Long> creditAdjust(@RequestBody CreditAdjustDTO dto) {
        requireRole(4);
        Long uid = dto.resolvedUserId();
        if (uid == null || dto.changeValue == null || dto.changeValue == 0) {
            throw new BizException("参数无效：userId/changeValue必填且changeValue不能为0");
        }
        if (dto.remark == null || dto.remark.trim().isEmpty()) {
            throw new BizException("操作理由为必填项，请详细填写调整原因");
        }
        if (dto.remark.trim().length() < 2) {
            throw new BizException("操作理由至少2个字");
        }
        if (Math.abs(dto.changeValue) > 1_000_000_000L) {
            throw new BizException("单次变动值过大");
        }
        User target = userMapper.selectById(uid);
        if (target == null) throw new BizException("目标用户不存在");

        Long opId = UserContext.getUserId();
        String opName = UserContext.getUsername();
        Integer opRole = UserContext.getRole();

        // 如果是负数扣目标，不能让目标扣成负数
        if (dto.changeValue < 0) {
            long cur = target.getCredits() == null ? 0L : target.getCredits();
            if (cur + dto.changeValue < 0) {
                throw new BizException("目标用户游戏币不足，当前=" + cur + "，想扣 " + (-dto.changeValue));
            }
        }

        String prefix = (opRole != null && opRole == 5) ? "[超管调整]" : "[客服调整]";
        String fullRemark = prefix + "(" + opName + ")" + dto.remark.trim()
                + " 变动=" + (dto.changeValue > 0 ? "+" : "") + dto.changeValue;

        Long after = creditService.adminAdjust(uid, dto.changeValue, fullRemark);
        // 补扣可能存在的欠费（若是代理）
        settlementService.repayAgentFeeFailure(uid);
        log.info("游戏币人工调整 op={}({}) -> target={}({}) change={}",
                opName, opRole, target.getUsername(), target.getRole(), dto.changeValue);
        return R.ok(after);
    }

    /** 查询积分流水（任意用户/分页） */
    @GetMapping("/credit/logs")
    public R<Page<CreditLog>> creditLogs(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "20") Integer size,
                                         @RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) Long operatorId,
                                         @RequestParam(required = false) Integer changeType,
                                         @RequestParam(required = false) String roomNo,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime) {
        requireRole(4);
        LambdaQueryWrapper<CreditLog> q = new LambdaQueryWrapper<>();
        if (userId != null) q.eq(CreditLog::getUserId, userId);
        if (operatorId != null) q.eq(CreditLog::getOperatorId, operatorId);
        if (changeType != null) q.eq(CreditLog::getChangeType, changeType);
        if (roomNo != null && !roomNo.isEmpty()) q.eq(CreditLog::getRoomNo, roomNo);
        if (startTime != null && !startTime.isEmpty()) q.ge(CreditLog::getCreateTime, startTime);
        if (endTime != null && !endTime.isEmpty()) q.le(CreditLog::getCreateTime, endTime);
        q.orderByDesc(CreditLog::getCreateTime);
        return R.ok(creditLogMapper.selectPage(new Page<>(page, size), q));
    }

    // ========== 水费扣费记录（最近10条/代理维度）==========

    @GetMapping("/water-fee-logs")
    public R<Page<AgentWaterFeeLog>> waterFeeLogs(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size,
                                                  @RequestParam(required = false) Long agentId,
                                                  @RequestParam(required = false) Integer status) {
        requireRole(4);
        LambdaQueryWrapper<AgentWaterFeeLog> q = new LambdaQueryWrapper<>();
        if (agentId != null) q.eq(AgentWaterFeeLog::getAgentId, agentId);
        if (status != null) q.eq(AgentWaterFeeLog::getStatus, status);
        q.orderByDesc(AgentWaterFeeLog::getId);
        return R.ok(waterFeeLogMapper.selectPage(new Page<>(page, size), q));
    }

    // ========== 总代理分润记录 ==========

    @GetMapping("/commissions")
    public R<Page<GeneralAgentCommission>> commissions(@RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "20") Integer size,
                                                       @RequestParam(required = false) Long generalAgentId,
                                                       @RequestParam(required = false) Long sourceAgentId) {
        requireRole(4);
        LambdaQueryWrapper<GeneralAgentCommission> q = new LambdaQueryWrapper<>();
        if (generalAgentId != null) q.eq(GeneralAgentCommission::getGeneralAgentId, generalAgentId);
        if (sourceAgentId != null) q.eq(GeneralAgentCommission::getSourceAgentId, sourceAgentId);
        q.orderByDesc(GeneralAgentCommission::getId);
        return R.ok(commissionMapper.selectPage(new Page<>(page, size), q));
    }

    // ========== 公告 ==========

    @GetMapping("/announcements")
    public R<List<PlatformAnnouncement>> announcements(@RequestParam(required = false) Boolean publishedOnly) {
        LambdaQueryWrapper<PlatformAnnouncement> q = new LambdaQueryWrapper<>();
        if (publishedOnly != null && publishedOnly) q.eq(PlatformAnnouncement::getStatus, 1);
        q.orderByDesc(PlatformAnnouncement::getTopFlag).orderByDesc(PlatformAnnouncement::getCreateTime);
        return R.ok(annMapper.selectList(q));
    }

    @PostMapping("/announcements")
    public R<PlatformAnnouncement> createAnnouncement(@RequestBody PlatformAnnouncement ann) {
        requireRole(5);
        ann.setPublisherId(UserContext.getUserId());
        ann.setPublisherName(UserContext.getUsername());
        if (ann.getStatus() == null) ann.setStatus(1);
        annMapper.insert(ann);
        return R.ok(ann);
    }

    @PutMapping("/announcements/{id}")
    public R<PlatformAnnouncement> updateAnnouncement(@PathVariable Long id,
                                                      @RequestBody PlatformAnnouncement ann) {
        requireRole(5);
        ann.setId(id);
        annMapper.updateById(ann);
        return R.ok(ann);
    }

    @DeleteMapping("/announcements/{id}")
    public R<Void> deleteAnnouncement(@PathVariable Long id) {
        requireRole(5);
        annMapper.deleteById(id);
        return R.ok();
    }

    // ========== 投诉/工单 ==========

    @PostMapping("/complaints")
    public R<PlatformComplaint> createComplaint(@RequestBody PlatformComplaint c) {
        c.setUserId(UserContext.getUserId());
        c.setUsername(UserContext.getUsername());
        c.setStatus(0);
        complaintMapper.insert(c);
        return R.ok(c);
    }

    @GetMapping("/complaints")
    public R<Page<PlatformComplaint>> complaints(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "20") Integer size,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Long userId) {
        requireRole(4);
        LambdaQueryWrapper<PlatformComplaint> q = new LambdaQueryWrapper<>();
        if (status != null) q.eq(PlatformComplaint::getStatus, status);
        if (userId != null) q.eq(PlatformComplaint::getUserId, userId);
        q.orderByDesc(PlatformComplaint::getCreateTime);
        return R.ok(complaintMapper.selectPage(new Page<>(page, size), q));
    }

    public static class HandleComplaintDTO {
        public Integer status;
        public String reply;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
    }

    @PostMapping("/complaints/{id}/handle")
    public R<PlatformComplaint> handleComplaint(@PathVariable Long id,
                                                @RequestBody HandleComplaintDTO dto) {
        requireRole(4);
        PlatformComplaint c = complaintMapper.selectById(id);
        if (c == null) throw new BizException("投诉不存在");
        c.setStatus(dto.status == null ? 2 : dto.status);
        c.setReply(dto.reply);
        c.setHandlerId(UserContext.getUserId());
        c.setHandlerName(UserContext.getUsername());
        c.setHandleTime(LocalDateTime.now());
        complaintMapper.updateById(c);
        return R.ok(c);
    }

    // ========== 超管首页统计 ==========

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        requireRole(5);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> res = new HashMap<>();
        // 总数
        long totalPlayers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.PLAYER.getCode()));
        long totalAgents = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.AGENT.getCode()));
        long totalGAs = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.GENERAL_AGENT.getCode()));
        long totalPrimaryAgents = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.PRIMARY_AGENT.getCode()));
        long totalRooms = roomMapper.selectCount(new LambdaQueryWrapper<>());
        long totalTurnover = 0L;
        List<GameRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<>());
        for (GameRoom r : rooms) totalTurnover += r.getTotalTurnover() == null ? 0L : r.getTotalTurnover();
        // 今日新增
        long todayPlayers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.PLAYER.getCode())
                .ge(User::getCreateTime, todayStart));
        long todayAgents = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.AGENT.getCode())
                .ge(User::getCreateTime, todayStart));
        long todayGAs = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.GENERAL_AGENT.getCode())
                .ge(User::getCreateTime, todayStart));
        long todayPrimaryAgents = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.PRIMARY_AGENT.getCode())
                .ge(User::getCreateTime, todayStart));
        // 待处理投诉
        long pendingComplaints = complaintMapper.selectCount(new LambdaQueryWrapper<PlatformComplaint>()
                .in(PlatformComplaint::getStatus, 0, 1));
        // 待修复的水费失败记录数
        long feeFails = waterFeeLogMapper.selectCount(new LambdaQueryWrapper<AgentWaterFeeLog>()
                .eq(AgentWaterFeeLog::getStatus, 1).eq(AgentWaterFeeLog::getRepaid, false));

        res.put("totalPlayers", totalPlayers);
        res.put("totalAgents", totalAgents);
        res.put("totalGeneralAgents", totalGAs);
        res.put("totalPrimaryAgents", totalPrimaryAgents);
        res.put("totalRooms", totalRooms);
        res.put("totalTurnover", totalTurnover);
        res.put("todayPlayers", todayPlayers);
        res.put("todayAgents", todayAgents);
        res.put("todayGeneralAgents", todayGAs);
        res.put("todayPrimaryAgents", todayPrimaryAgents);
        res.put("pendingComplaints", pendingComplaints);
        res.put("pendingFeeFailures", feeFails);

        // ========== 今日工作台统计（超管首页要看的）==========
        // 今日开了多少桌
        long todayTables = roomMapper.selectCount(new LambdaQueryWrapper<GameRoom>()
                .ge(GameRoom::getCreateTime, todayStart));
        // 今日对局多少次
        long todayRounds = roundMapper.selectCount(new LambdaQueryWrapper<GameRound>()
                .ge(GameRound::getCreateTime, todayStart));
        // 今日房间总抽水（所有房间当日产生的抽水之和；这里按 round 的 roundRake 汇总）
        long todayRake = 0L;
        List<GameRound> todayRoundList = roundMapper.selectList(new LambdaQueryWrapper<GameRound>()
                .ge(GameRound::getCreateTime, todayStart));
        for (GameRound r : todayRoundList) todayRake += r.getRoundRake() == null ? 0L : r.getRoundRake();
        // 今日反佣奖励（RakeRebateLog 的 rebateAmount）
        long todayRebate = 0L;
        List<RakeRebateLog> todayRebateList = rakeRebateLogMapper.selectList(new LambdaQueryWrapper<RakeRebateLog>()
                .ge(RakeRebateLog::getCreateTime, todayStart));
        for (RakeRebateLog r : todayRebateList) todayRebate += r.getRebateAmount() == null ? 0L : r.getRebateAmount();
        // 今日客服支出（changeType=7 表示客服调整；正值合计 + 负值绝对值合计）
        long todayCsOut = 0L;
        long todayCsDeduct = 0L;
        List<CreditLog> todayCsList = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getChangeType, 7)
                .ge(CreditLog::getCreateTime, todayStart));
        for (CreditLog cl : todayCsList) {
            if (cl.getChangeValue() == null) continue;
            if (cl.getChangeValue() > 0) todayCsOut += cl.getChangeValue();
            else todayCsDeduct += -cl.getChangeValue();
        }
        res.put("todayTables", todayTables);
        res.put("todayRounds", todayRounds);
        res.put("todayRake", todayRake);
        res.put("todayRebate", todayRebate);
        res.put("todayCsOut", todayCsOut);
        res.put("todayCsDeduct", todayCsDeduct);

        // ========== 今日水费扣费 + 净流出 ==========
        // 今日从代理账户实际扣除的水费总额（agent_water_fee_log.status=0 表示扣费成功）
        long todayWaterFeeDeduct = 0L;
        List<AgentWaterFeeLog> todayFeeLogs = waterFeeLogMapper.selectList(new LambdaQueryWrapper<AgentWaterFeeLog>()
                .ge(AgentWaterFeeLog::getCreateTime, todayStart)
                .eq(AgentWaterFeeLog::getStatus, 0));
        for (AgentWaterFeeLog f : todayFeeLogs) todayWaterFeeDeduct += f.getFeeAmount() == null ? 0L : f.getFeeAmount();
        // 净流出 = 抽水 - 反佣（平台今日真实盈收的视角）
        long todayNetOutflow = todayRake - todayRebate;
        res.put("todayWaterFeeDeduct", todayWaterFeeDeduct);
        res.put("todayNetOutflow", todayNetOutflow);
        return R.ok(res);
    }

    /**
     * 超管「所有房间」列表（分页 + 可选游戏类型 / 状态过滤）
     * 用于 WorkbenchPage 点击「所有房间」时拉取全平台房间。
     */
    @GetMapping("/rooms")
    public R<Map<String, Object>> allRooms(
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size
    ) {
        requireRole(5);
        LambdaQueryWrapper<GameRoom> q = new LambdaQueryWrapper<>();
        if (gameType != null && !gameType.isEmpty()) q.eq(GameRoom::getGameType, gameType);
        if (status != null) q.eq(GameRoom::getStatus, status);
        // 先取总数（不加分页）
        long total = roomMapper.selectCount(q);
        // 分页取记录
        q.orderByDesc(GameRoom::getCreateTime)
         .last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<GameRoom> list = roomMapper.selectList(q);
        Map<String, Object> res = new HashMap<>();
        res.put("records", list);
        res.put("total", total);
        res.put("page", page);
        res.put("size", size);
        return R.ok(res);
    }
}
