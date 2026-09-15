package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.config.PlatformConfig;
import com.poker.platform.dto.R;
import com.poker.platform.entity.*;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.*;
import com.poker.platform.security.UserContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 代理中心 & 房间结算（原型 28-代理中心 / 原型 10-房间结算 / 原型 25-我的房间列表）
 *
 * 原型 28 代理中心页面元素：
 *  皇冠徽章、代理等级 VIP3、预计今日佣金、佣金比例 15% + 金额 3,280
 *  四个功能按钮：会员管理、佣金记录、数据看板、推广链接
 *  提示「完整代理功能请登录代理后台」+「前往代理后台」按钮
 *
 * 原型 10 房间结算页面元素：
 *  总局数 25、总流水 1,286,000、平台抽水 64,300
 *  玩家战绩列表：昵称、盈亏（+58,600 / -20,300）、皇冠角标
 *  按钮：返回大厅、再来一局
 */
@RestController
@RequestMapping("/agent-center")
public class AgentCenterController {

    @Resource private UserMapper userMapper;
    @Resource private GameRoomMapper gameRoomMapper;
    @Resource private RoomPlayerMapper roomPlayerMapper;
    @Resource private RakeRebateLogMapper rakeRebateLogMapper;
    @Resource private AgentWaterFeeLogMapper waterFeeLogMapper;
    @Resource private GeneralAgentCommissionMapper commissionMapper;
    @Resource private PlatformConfig platformConfig;

    /**
     * 代理中心首页（原型 28）
     * 返回：代理等级、佣金比例、预计今日佣金、功能入口
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) {
            throw new BizException("仅代理可访问代理中心");
        }
        User me = userMapper.selectById(uid);
        if (me == null) throw new BizException("用户不存在");

        double rate = commissionRate(role);
        long todayCommission = todayCommission(uid);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("nickname", me.getNickname());
        res.put("avatar", me.getAvatar());
        res.put("credits", me.getCredits());
        res.put("role", role);
        UserRole r = UserRole.fromCode(role);
        res.put("roleDesc", r == null ? "" : r.getDesc());
        // 原型 28「代理等级 VIP3」——按角色与下级规模映射为 VIP 等级
        res.put("agentLevel", vipLevel(role, countSubordinates(uid)));
        res.put("commissionRate", rate);
        res.put("commissionRateLabel", trimPercent(rate) + "%");
        res.put("todayCommission", todayCommission);
        res.put("estimatedCommission", todayCommission);   // 原型「预计今日佣金」
        res.put("entries", entries());
        res.put("tip", "完整代理功能请登录代理后台");
        res.put("backendUrl", "/admin");
        return R.ok(res);
    }

    /**
     * 会员管理（原型 28「会员管理」入口）
     * 返回下线列表：昵称、ID、角色、游戏币、累计流水
     */
    @GetMapping("/members")
    public R<Map<String, Object>> members(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) throw new BizException("仅代理可访问");

        Page<User> p = userMapper.selectPage(
                new Page<>(page == null || page < 1 ? 1 : page, size == null || size < 1 ? 20 : Math.min(size, 100)),
                new LambdaQueryWrapper<User>()
                        .eq(User::getParentId, uid)
                        .orderByDesc(User::getCreateTime));

        List<Map<String, Object>> records = new ArrayList<>();
        for (User u : p.getRecords()) {
            UserRole ur = UserRole.fromCode(u.getRole());
            List<GameRoom> rooms = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                    .eq(GameRoom::getOwnerId, u.getId()));
            long turnover = rooms.stream()
                    .filter(x -> x.getTotalTurnover() != null)
                    .mapToLong(GameRoom::getTotalTurnover).sum();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("role", u.getRole());
            m.put("roleDesc", ur == null ? "" : ur.getDesc());
            m.put("credits", u.getCredits());
            m.put("inviteCode", u.getInviteCode());
            m.put("totalTurnover", turnover);
            m.put("roomCount", rooms.size());
            m.put("status", u.getStatus());
            m.put("createTime", u.getCreateTime());
            records.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("records", records);
        res.put("total", p.getTotal());
        res.put("current", p.getCurrent());
        res.put("size", p.getSize());
        res.put("pages", p.getPages());
        return R.ok(res);
    }

    /**
     * 佣金记录（原型 28「佣金记录」入口）
     * 聚合展示：水费扣费 + 返佣账单 + 总代分润
     */
    @GetMapping("/commissions")
    public R<Map<String, Object>> commissions(@RequestParam(defaultValue = "20") Integer size) {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) throw new BizException("仅代理可访问");
        int n = size == null || size < 1 ? 20 : Math.min(size, 100);

        List<Map<String, Object>> waterFees = new ArrayList<>();
        for (AgentWaterFeeLog l : waterFeeLogMapper.selectList(new LambdaQueryWrapper<AgentWaterFeeLog>()
                .eq(AgentWaterFeeLog::getAgentId, uid)
                .orderByDesc(AgentWaterFeeLog::getId).last("LIMIT " + n))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("type", "WATER_FEE");
            m.put("typeDesc", "开房水费");
            m.put("roomNo", l.getRoomNo());
            m.put("roomTurnover", l.getRoomTurnover());
            m.put("amount", -safe(l.getFeeAmount()));
            m.put("beforeCredit", l.getBeforeCredit());
            m.put("afterCredit", l.getAfterCredit());
            m.put("status", l.getStatus());
            m.put("failReason", l.getFailReason());
            m.put("createTime", l.getCreateTime());
            waterFees.add(m);
        }

        List<Map<String, Object>> rebates = new ArrayList<>();
        for (RakeRebateLog l : rakeRebateLogMapper.selectList(new LambdaQueryWrapper<RakeRebateLog>()
                .eq(RakeRebateLog::getOwnerId, uid)
                .orderByDesc(RakeRebateLog::getId).last("LIMIT " + n))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("type", "RAKE_REBATE");
            m.put("typeDesc", "系统返佣");
            m.put("roomNo", l.getRoomNo());
            m.put("roomTurnover", l.getRoomTurnover());
            m.put("deductedAmount", l.getDeductedAmount());
            m.put("amount", l.getRebateAmount());
            m.put("netCost", l.getNetCost());
            m.put("beforeCredit", l.getBeforeCredit());
            m.put("afterCredit", l.getAfterCredit());
            m.put("createTime", l.getCreateTime());
            rebates.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("waterFees", waterFees);
        res.put("rebates", rebates);
        res.put("totalWaterFee", waterFees.stream()
                .mapToLong(x -> Math.abs(((Number) x.get("amount")).longValue())).sum());
        res.put("totalRebate", rebates.stream()
                .mapToLong(x -> ((Number) x.get("amount")).longValue()).sum());
        return R.ok(res);
    }

    /**
     * 数据看板（原型 28「数据看板」入口）
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) throw new BizException("仅代理可访问");

        List<User> subs = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getParentId, uid));
        List<GameRoom> rooms = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getOwnerId, uid));

        long turnover = rooms.stream().filter(r -> r.getTotalTurnover() != null)
                .mapToLong(GameRoom::getTotalTurnover).sum();
        long rake = rooms.stream().filter(r -> r.getTotalRake() != null)
                .mapToLong(GameRoom::getTotalRake).sum();
        long waterFee = rooms.stream().filter(r -> r.getTotalWaterFee() != null)
                .mapToLong(GameRoom::getTotalWaterFee).sum();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("subordinateCount", subs.size());
        res.put("roomCount", rooms.size());
        res.put("totalTurnover", turnover);
        res.put("totalRake", rake);
        res.put("totalWaterFee", waterFee);
        res.put("todayCommission", todayCommission(uid));
        res.put("commissionRate", commissionRate(role));
        res.put("runningRooms", rooms.stream()
                .filter(r -> r.getStatus() != null && (r.getStatus() == 0 || r.getStatus() == 1)).count());
        res.put("settledRooms", rooms.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 2).count());
        return R.ok(res);
    }

    /**
     * 推广链接（原型 28「推广链接」入口）
     */
    @GetMapping("/promotion")
    public R<Map<String, Object>> promotion() {
        Long uid = UserContext.getUserId();
        User me = userMapper.selectById(uid);
        if (me == null) throw new BizException("用户不存在");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("inviteCode", me.getInviteCode());
        res.put("inviteLink", "https://v-poker.com/r/" + me.getInviteCode());
        res.put("qrCode", null);
        res.put("shareTitle", "V-POKER 龙腾竞技平台");
        res.put("shareDesc", "使用邀请码 " + me.getInviteCode() + " 注册，加入我的团队");
        return R.ok(res);
    }

    // ==================== 房间结算详情（原型 10） ====================

    /**
     * 房间结算详情（原型 10-房间结算页）
     *
     * 页面元素：总局数、总流水、平台抽水、玩家战绩列表（昵称 + 盈亏 + 皇冠角标）
     */
    @GetMapping("/room-settlement/{roomId}")
    public R<Map<String, Object>> roomSettlement(@PathVariable Long roomId) {
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");

        List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsObserver, false));

        // 补昵称与头像（room_player 已存昵称，头像从 user 表补）
        List<Map<String, Object>> list = new ArrayList<>();
        long bestProfit = Long.MIN_VALUE;
        for (RoomPlayer rp : players) {
            User u = userMapper.selectById(rp.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", rp.getUserId());
            m.put("username", rp.getUsername());
            m.put("nickname", rp.getNickname() != null ? rp.getNickname()
                    : (u == null ? rp.getUsername() : u.getNickname()));
            m.put("avatar", u == null ? null : u.getAvatar());
            m.put("seatNo", rp.getSeatNo());
            m.put("buyinCredits", rp.getBuyinCredits());
            m.put("finalCredits", rp.getCurrentCredits());
            long profit = rp.getTotalProfit() == null ? 0L : rp.getTotalProfit();
            m.put("profit", profit);
            m.put("win", profit > 0);
            list.add(m);
            if (profit > bestProfit) bestProfit = profit;
        }
        // 标记最大赢家（原型 10 皇冠角标 + 高亮描边）
        for (Map<String, Object> m : list) {
            boolean champion = ((Number) m.get("profit")).longValue() == bestProfit && bestProfit > 0;
            m.put("champion", champion);
            m.put("crown", champion);
        }
        list.sort((a, b) -> Long.compare(((Number) b.get("profit")).longValue(),
                ((Number) a.get("profit")).longValue()));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("roomId", roomId);
        res.put("roomNo", room.getRoomNo());
        res.put("roomName", room.getRoomName());
        res.put("gameType", room.getGameType());
        res.put("roomLevel", room.getRoomLevel());
        res.put("status", room.getStatus());
        res.put("totalRounds", room.getTotalRounds());                       // 原型「总局数 25」
        res.put("currentRound", room.getCurrentRound());
        res.put("totalTurnover", safe(room.getTotalTurnover()));             // 原型「总流水 1,286,000」
        res.put("totalRake", safe(room.getTotalRake()));                     // 原型「平台抽水 64,300」
        res.put("totalWaterFee", safe(room.getTotalWaterFee()));
        res.put("players", list);
        res.put("actions", actions());
        return R.ok(res);
    }

    /** 房间结算页操作按钮（原型 10：返回大厅 / 再来一局） */
    private List<Map<String, Object>> actions() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(action("BACK_LOBBY", "返回大厅", "default", false));
        list.add(action("PLAY_AGAIN", "再来一局", "primary", true));
        return list;
    }

    private Map<String, Object> action(String code, String name, String type, boolean primary) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("type", type);
        m.put("primary", primary);
        return m;
    }

    // ==================== 工具 ====================

    /** 代理中心四个功能入口（原型 28） */
    private List<Map<String, Object>> entries() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(entry("MEMBERS", "会员管理", "gold", "/agent-center/members"));
        list.add(entry("COMMISSIONS", "佣金记录", "purple", "/agent-center/commissions"));
        list.add(entry("DASHBOARD", "数据看板", "blue", "/agent-center/dashboard"));
        list.add(entry("PROMOTION", "推广链接", "green", "/agent-center/promotion"));
        return list;
    }

    private Map<String, Object> entry(String code, String name, String color, String api) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("color", color);
        m.put("api", api);
        return m;
    }

    /** 按角色返回佣金比例（原型 28「佣金比例 15%」） */
    /**
     * 我的佣金比例（原型 28-代理中心「佣金比例 15%」）
     *
     * 接口文档第十一节列为 P1 缺口：「无接口下发当前用户适用的佣金比例」。
     * 此处将代理中心首页散落的比例一次性下发，供前端代理中心 / 个人中心直接渲染，
     * 避免前端硬编码比例导致规则变更时不一致。
     *
     * 返回：
     *   role / roleDesc   当前角色
     *   commissionRate    该角色推广分佣比例（%）：二级 10 / 一级 12 / 总代 15
     *   waterFeeRate      该角色开房水费比例（%）：二级 1 / 一级 1.5 / 总代 2
     *   rakePercent       平台单局抽水比例（%），全员一致
     */
    @GetMapping("/commission-rate")
    public R<Map<String, Object>> commissionRate() {
        Long uid = UserContext.getUserId();
        Integer role = UserContext.getRole();
        if (role == null || !UserRole.isAgent(role)) {
            throw new BizException("仅代理可访问代理中心");
        }
        User me = userMapper.selectById(uid);
        if (me == null) throw new BizException("用户不存在");

        UserRole ur = UserRole.fromCode(role);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("role", role);
        res.put("roleDesc", ur == null ? "" : ur.getDesc());
        res.put("commissionRate", commissionRate(role));
        res.put("waterFeeRate", waterFeeRate(role));
        res.put("rakePercent", platformConfig.getRakePercent());
        res.put("publicMatchTotalRakePercent", platformConfig.getPublicMatchTotalRakePercent());
        res.put("publicMatchAgentCommissionPercent", platformConfig.getPublicMatchAgentCommissionPercent());
        res.put("roundsPerRoom", platformConfig.getRoundsPerRoom());
        return R.ok(res);
    }

    /**
     * 按角色返回开房水费比例（%）：总代 2 / 一级 1.5 / 二级 1
     * 与 SettlementService.resolveWaterFeePercent 口径一致，此处仅用于展示下发。
     */
    private double waterFeeRate(Integer role) {
        if (role == null) return 0d;
        switch (role) {
            case 2: return platformConfig.getWaterFeeSecondaryAgentPercent();
            case 6: return platformConfig.getWaterFeePrimaryAgentPercent();
            case 3: return platformConfig.getWaterFeeGeneralAgentPercent();
            default: return 0d;
        }
    }

    private double commissionRate(Integer role) {
        if (role == null) return 0d;
        switch (role) {
            case 2: return 10d;   // 二级代理
            case 6: return 12d;   // 一级代理
            case 3: return 15d;   // 总代理
            default: return 0d;
        }
    }

    private String trimPercent(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /** VIP 等级映射（原型 28「代理等级 VIP3」） */
    private String vipLevel(Integer role, int subCount) {
        if (role == null) return "VIP0";
        switch (role) {
            case 3: return "VIP5";
            case 6: return "VIP4";
            case 2: return subCount >= 10 ? "VIP3" : "VIP2";
            default: return "VIP0";
        }
    }

    private int countSubordinates(Long uid) {
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getParentId, uid));
        return c == null ? 0 : c.intValue();
    }

    /** 今日佣金（按今日产生的返佣与分润汇总） */
    private long todayCommission(Long uid) {
        java.time.LocalDateTime start = java.time.LocalDate.now().atStartOfDay();
        long sum = 0L;
        for (RakeRebateLog l : rakeRebateLogMapper.selectList(new LambdaQueryWrapper<RakeRebateLog>()
                .eq(RakeRebateLog::getOwnerId, uid)
                .ge(RakeRebateLog::getCreateTime, start))) {
            sum += safe(l.getRebateAmount());
        }
        for (GeneralAgentCommission l : commissionMapper.selectList(new LambdaQueryWrapper<GeneralAgentCommission>()
                .eq(GeneralAgentCommission::getGeneralAgentId, uid)
                .ge(GeneralAgentCommission::getCreateTime, start))) {
            sum += safe(l.getCommissionAmount());
        }
        return sum;
    }

    private long safe(Long v) { return v == null ? 0L : v; }
}
