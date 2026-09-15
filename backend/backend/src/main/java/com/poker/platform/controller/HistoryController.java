package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.entity.CreditLog;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.GameRound;
import com.poker.platform.entity.User;
import com.poker.platform.enums.CreditChangeType;
import com.poker.platform.enums.GameType;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.GameRoundMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 战绩记录 & 资金流水（原型 18-战绩记录页 / 原型 19-资金流水页）
 *
 * 原型 18 页面元素：
 *  Tab：全部 / 德州 / 金花 / 三公 / 牛牛
 *  卡片：玩法名、房号、局数、流水、时间、盈亏（正金色 / 负红色）
 *  空状态：暂无战绩「还没有游戏记录，快去开始第一局吧」+「去游戏大厅」
 *
 * 原型 19 页面元素：
 *  Tab：全部 / 充值 / 提现 / 佣金 / 赠送 / 对局
 *  行：类型、时间、说明、变动、余额
 *  空状态：暂无流水
 */
@RestController
@RequestMapping("/history")
public class HistoryController {

    @Resource private GameRoundMapper gameRoundMapper;
    @Resource private GameRoomMapper gameRoomMapper;
    @Resource private CreditLogMapper creditLogMapper;
    @Resource private UserMapper userMapper;

    /**
     * 战绩记录（原型 18）
     *
     * @param tab  全部(不传/ALL) / 德州(TEXAS) / 金花(JINHUA) / 三公(SANGONG) / 牛牛(DOUNIU)
     *             注：「三公」同时匹配 SANGONG 与 TONGBI_SANGONG；「牛牛」同时匹配 DOUNIU 与 TONGBI_NIUNIU
     */
    @GetMapping("/game-records")
    public R<Map<String, Object>> gameRecords(@RequestParam(required = false) String tab,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size) {
        Long uid = UserContext.getUserId();
        String uidStr = String.valueOf(uid);

        // 1) 查出该用户参与过的所有房间（赢家或输家列表包含该用户）
        List<GameRound> rounds = gameRoundMapper.selectList(new LambdaQueryWrapper<GameRound>()
                .like(GameRound::getWinnerIds, uidStr).or().like(GameRound::getLoserIds, uidStr)
                .orderByDesc(GameRound::getId));

        // 2) 按房间聚合为「战绩卡片」：局数、流水、输赢、时间、玩法
        Map<String, Agg> aggMap = new LinkedHashMap<>();
        for (GameRound r : rounds) {
            String key = r.getRoomNo() == null ? String.valueOf(r.getRoomId()) : r.getRoomNo();
            Agg agg = aggMap.get(key);
            if (agg == null) {
                agg = new Agg();
                agg.roomNo = key;
                agg.roomId = r.getRoomId();
                agg.firstTime = r.getCreateTime();
                agg.lastTime = r.getCreateTime();
                aggMap.put(key, agg);
            }
            agg.rounds += 1;
            agg.turnover += r.getRoundTurnover() == null ? 0L : r.getRoundTurnover();
            agg.rake += r.getRoundRake() == null ? 0L : r.getRoundRake();
            // 判断该用户本局输赢：从 settlementDetail 无法精确解析单局净盈亏时，
            // 以 winnerIds 是否包含当前用户作为输赢近似（与旧接口一致）
            boolean win = r.getWinnerIds() != null && r.getWinnerIds().contains(uidStr);
            if (win) agg.winRounds += 1; else agg.loseRounds += 1;
            if (r.getCreateTime() != null) {
                if (agg.firstTime == null || r.getCreateTime().isBefore(agg.firstTime)) agg.firstTime = r.getCreateTime();
                if (agg.lastTime == null || r.getCreateTime().isAfter(agg.lastTime)) agg.lastTime = r.getCreateTime();
            }
        }

        // 3) 补玩法信息（房间号 -> gameType）
        List<String> roomNos = new ArrayList<>(aggMap.keySet());
        Map<String, String> roomGameType = new HashMap<>();
        Map<String, String> roomLevel = new HashMap<>();
        if (!roomNos.isEmpty()) {
            List<GameRoom> rooms = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                    .in(GameRoom::getRoomNo, roomNos));
            for (GameRoom rm : rooms) {
                roomGameType.put(rm.getRoomNo(), rm.getGameType());
                roomLevel.put(rm.getRoomNo(), rm.getRoomLevel());
            }
        }

        // 4) Tab 筛选
        Set<String> allowed = tabGameTypes(tab);
        List<Map<String, Object>> all = new ArrayList<>();
        for (Agg agg : aggMap.values()) {
            String gt = roomGameType.get(agg.roomNo);
            if (allowed != null && (gt == null || !allowed.contains(gt))) continue;

            GameType t = GameType.fromCode(gt);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roomNo", agg.roomNo);
            m.put("roomId", agg.roomId);
            m.put("gameType", gt);
            m.put("gameName", t == null ? "未知玩法" : clientName(t));
            m.put("roomLevel", roomLevel.get(agg.roomNo));
            m.put("rounds", agg.rounds);
            m.put("roundsLabel", agg.rounds + "局");
            m.put("turnover", agg.turnover);
            m.put("rake", agg.rake);
            m.put("winRounds", agg.winRounds);
            m.put("loseRounds", agg.loseRounds);
            // 盈亏：以房间累计抽水反向估算不可靠，改为「赢局数-输局数」标记 + 房间级净盈亏由结算页提供
            m.put("win", agg.winRounds >= agg.loseRounds);
            m.put("time", agg.lastTime);
            all.add(m);
        }

        // 5) 内存分页
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        int from = Math.min((p - 1) * s, all.size());
        int to = Math.min(from + s, all.size());
        List<Map<String, Object>> records = all.subList(from, to);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("records", records);
        res.put("total", all.size());
        res.put("current", p);
        res.put("size", s);
        res.put("pages", s == 0 ? 0 : (all.size() + s - 1) / s);
        res.put("tabs", gameTabs());
        res.put("empty", all.isEmpty());
        res.put("emptyText", "暂无战绩");
        res.put("emptySubText", "还没有游戏记录，快去开始第一局吧");
        return R.ok(res);
    }

    /**
     * 资金流水（原型 19）
     *
     * @param tab 全部(不传/ALL) / 充值(RECHARGE) / 提现(WITHDRAW) / 佣金(COMMISSION) / ���送(GIFT) / 对局(GAME)
     */
    @GetMapping("/credit-records")
    public R<Map<String, Object>> creditRecords(@RequestParam(required = false) String tab,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size) {
        Long uid = UserContext.getUserId();
        LambdaQueryWrapper<CreditLog> q = new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, uid);

        Set<Integer> types = CreditChangeType.tabTypes(tab);
        if (types != null) {
            if (types.isEmpty()) {
                // Tab 无匹配类型 → 直接返回空页
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0L);
                empty.put("tabs", creditTabs());
                empty.put("empty", true);
                empty.put("emptyText", "暂无流水");
                return R.ok(empty);
            }
            q.in(CreditLog::getChangeType, types);
        }
        q.orderByDesc(CreditLog::getId);

        Page<CreditLog> result = creditLogMapper.selectPage(
                new Page<>(page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : Math.min(size, 100)), q);

        // 附加类型描述，便于前端直接展示「充值/提现/佣金/赠送/对局」
        List<Map<String, Object>> records = result.getRecords().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("changeType", l.getChangeType());
            m.put("typeDesc", CreditChangeType.descOf(l.getChangeType()));
            m.put("tab", CreditChangeType.tabOf(l.getChangeType()));
            m.put("changeValue", l.getChangeValue());
            m.put("beforeValue", l.getBeforeValue());
            m.put("afterValue", l.getAfterValue());
            m.put("roomNo", l.getRoomNo());
            m.put("remark", l.getRemark());
            m.put("createTime", l.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("records", records);
        res.put("total", result.getTotal());
        res.put("current", result.getCurrent());
        res.put("size", result.getSize());
        res.put("pages", result.getPages());
        res.put("tabs", creditTabs());
        res.put("empty", records.isEmpty());
        res.put("emptyText", "暂无流水");
        return R.ok(res);
    }

    /**
     * 战绩记录（旧接口保留，按单局返回）
     * 与 /user/game-history 等价，便于原型 18 切换到「按局」视图。
     */
    @GetMapping("/rounds")
    public R<Page<Map<String, Object>>> rounds(@RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer size) {
        Long uid = UserContext.getUserId();
        String uidStr = String.valueOf(uid);
        Page<GameRound> result = gameRoundMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<GameRound>()
                        .like(GameRound::getWinnerIds, uidStr).or().like(GameRound::getLoserIds, uidStr)
                        .orderByDesc(GameRound::getId));

        Page<Map<String, Object>> vo = new Page<>(page, size, result.getTotal());
        vo.setRecords(result.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("roomNo", r.getRoomNo());
            m.put("roundNo", r.getRoundNo());
            m.put("turnover", r.getRoundTurnover());
            m.put("rake", r.getRoundRake());
            m.put("win", r.getWinnerIds() != null && r.getWinnerIds().contains(uidStr));
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList()));
        return R.ok(vo);
    }

    /** 我的资金总览（原型 07 个人中心：余额 + 快捷入口） */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("credits", u == null || u.getCredits() == null ? 0L : u.getCredits());
        res.put("totalRecharge", sumByTab(uid, "RECHARGE"));
        res.put("totalWithdraw", sumByTab(uid, "WITHDRAW"));
        res.put("totalCommission", sumByTab(uid, "COMMISSION"));
        res.put("totalGame", sumByTab(uid, "GAME"));
        return R.ok(res);
    }

    private long sumByTab(Long uid, String tab) {
        Set<Integer> types = CreditChangeType.tabTypes(tab);
        if (types == null || types.isEmpty()) return 0L;
        List<CreditLog> logs = creditLogMapper.selectList(new LambdaQueryWrapper<CreditLog>()
                .eq(CreditLog::getUserId, uid)
                .in(CreditLog::getChangeType, types));
        long sum = 0L;
        for (CreditLog l : logs) sum += l.getChangeValue() == null ? 0L : l.getChangeValue();
        return sum;
    }

    /** 原型 18 Tab: 全部 / 德州 / 金花 / 三公 / 牛牛 */
    private List<Map<String, Object>> gameTabs() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(tab("ALL", "全部"));
        list.add(tab("TEXAS", "德州"));
        list.add(tab("JINHUA", "金花"));
        list.add(tab("SANGONG", "三公"));
        list.add(tab("DOUNIU", "牛牛"));
        return list;
    }

    /** 原型 19 Tab: 全部 / 充值 / 提现 / 佣金 / 赠送 / 对局 */
    private List<Map<String, Object>> creditTabs() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(tab("ALL", "全部"));
        list.add(tab("RECHARGE", "充值"));
        list.add(tab("WITHDRAW", "提现"));
        list.add(tab("COMMISSION", "佣金"));
        list.add(tab("GIFT", "赠送"));
        list.add(tab("GAME", "对局"));
        return list;
    }

    private Map<String, Object> tab(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    /**
     * Tab → 玩法集合映射
     * 三公 = 抢庄三公 + 通比三公；牛牛 = 抢庄牛牛 + 通比牛牛
     */
    private Set<String> tabGameTypes(String tab) {
        if (tab == null || tab.isEmpty() || "ALL".equalsIgnoreCase(tab)) return null;
        String t = tab.trim().toUpperCase();
        Set<String> set = new HashSet<>();
        switch (t) {
            case "TEXAS":
            case "德州":
                set.add(GameType.TEXAS.getCode());
                break;
            case "JINHUA":
            case "金花":
                set.add(GameType.JINHUA.getCode());
                break;
            case "SANGONG":
            case "三公":
                set.add(GameType.SANGONG.getCode());
                set.add(GameType.TONGBI_SANGONG.getCode());
                break;
            case "DOUNIU":
            case "牛牛":
                set.add(GameType.DOUNIU.getCode());
                set.add(GameType.TONGBI_NIUNIU.getCode());
                break;
            default:
                return null;
        }
        return set;
    }

    /** 客户端玩法展示名（原型 18 卡片标题口径） */
    private String clientName(GameType t) {
        switch (t) {
            case TEXAS: return "德州扑克";
            case JINHUA: return "炸金花";
            case SANGONG: return "抢庄三公";
            case DOUNIU: return "抢庄牛牛";
            case TONGBI_NIUNIU: return "通比牛牛";
            case TONGBI_SANGONG: return "通比三公";
            default: return t.getDesc();
        }
    }

    /** 房间级聚合累加器 */
    private static class Agg {
        String roomNo;
        Long roomId;
        int rounds;
        int winRounds;
        int loseRounds;
        long turnover;
        long rake;
        java.time.LocalDateTime firstTime;
        java.time.LocalDateTime lastTime;
    }
}
