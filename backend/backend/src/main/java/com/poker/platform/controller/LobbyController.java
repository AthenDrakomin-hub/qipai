package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.dto.R;
import com.poker.platform.entity.OnlinePlayer;
import com.poker.platform.entity.User;
import com.poker.platform.enums.GameType;
import com.poker.platform.enums.UserRole;
import com.poker.platform.mapper.OnlinePlayerMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏大厅（原型 04-游戏大厅-玩家视角 / 原型 11-游戏大厅-代理视角）
 *
 * 顶部信息：头像、昵称、金币 25,860、钻石 0、铃铛红点、代理标签
 * 公告条：欢迎来到V-POKER 新用户注册送百万金币
 * 左侧「在线玩家」列表：头像、昵称、金币数值
 * 右侧主按钮：玩家视角 3 个（普通场/百人场/赛事场）；代理视角 4 个（+ 创建房间）
 * 底部 Tab：玩家 4 项（商城/消息/菜单/日历）；代理 4 项（首页/房间/代理中心/我的）
 */
@RestController
@RequestMapping("/lobby")
public class LobbyController {

    @Resource private UserMapper userMapper;
    @Resource private OnlinePlayerMapper onlinePlayerMapper;
    @Resource private com.poker.platform.mapper.PlatformAnnouncementMapper announcementMapper;
    @Resource private com.poker.platform.service.MessageService messageService;

    /**
     * 大厅首页数据装配（玩家/代理视角共用）
     * 前端按 role 决定是否渲染「创建房间」「代理标签」及底部 Tab 项
     */
    @GetMapping("/home")
    public R<Map<String, Object>> home() {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);

        Map<String, Object> res = new LinkedHashMap<>();
        // 顶部用户信息
        res.put("avatar", u == null ? null : u.getAvatar());
        res.put("nickname", u == null ? null : u.getNickname());
        res.put("credits", u == null || u.getCredits() == null ? 0L : u.getCredits());
        res.put("diamond", 0);                       // 原型 04/11「钻石 0」
        res.put("role", u == null ? null : u.getRole());
        UserRole r = u == null ? null : UserRole.fromCode(u.getRole());
        res.put("roleDesc", r == null ? "" : r.getDesc());
        res.put("isAgent", u != null && UserRole.isAgent(u.getRole()));  // 原型「代理」标签
        res.put("hasFeeFailure", u != null && Boolean.TRUE.equals(u.getHasFeeFailure()));

        // 未读消息红点（原型 04/11 铃铛红点）
        res.put("unreadCount", messageService.unreadCount(uid).get("total"));

        // 公告滚动条（原型 04/11）
        res.put("announcements", announcementMapper.selectList(
                new LambdaQueryWrapper<com.poker.platform.entity.PlatformAnnouncement>()
                        .eq(com.poker.platform.entity.PlatformAnnouncement::getStatus, 1)
                        .orderByDesc(com.poker.platform.entity.PlatformAnnouncement::getTopFlag)
                        .orderByDesc(com.poker.platform.entity.PlatformAnnouncement::getCreateTime)
                        .last("LIMIT 5")));

        // 游戏入口 ×6（原型 04/05 六种玩法）
        res.put("gameEntries", gameEntries());

        // 主按钮（原型 04/11：普通场/百人场/赛事场 + 代理额外「创建房间」）
        res.put("mainEntries", mainEntries(u != null && UserRole.isAgent(u.getRole())));
        return R.ok(res);
    }

    /**
     * 在线玩家列表（原型 04/11 左侧「在线玩家」）
     * 返回头像、昵称、金币数值
     */
    @GetMapping("/online-players")
    public R<List<Map<String, Object>>> onlinePlayers(@RequestParam(defaultValue = "20") Integer limit) {
        int n = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        // 优先读在线快照表；快照为空时回退到用户表（保证前端首屏不空）
        List<OnlinePlayer> snapshots = onlinePlayerMapper.selectList(new LambdaQueryWrapper<OnlinePlayer>()
                .orderByDesc(OnlinePlayer::getLastActiveTime)
                .last("LIMIT " + n));

        List<Map<String, Object>> list = new ArrayList<>();
        if (!snapshots.isEmpty()) {
            for (OnlinePlayer p : snapshots) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", p.getUserId());
                m.put("nickname", p.getNickname());
                m.put("avatar", p.getAvatar());
                m.put("credits", p.getCredits());
                m.put("gameType", p.getGameType());
                m.put("roomNo", p.getRoomNo());
                m.put("lastActiveTime", p.getLastActiveTime());
                list.add(m);
            }
            return R.ok(list);
        }

        // 回退：从用户表取在线玩家（按最近活跃时间）
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.PLAYER.getCode())
                .orderByDesc(User::getLastLoginTime)
                .last("LIMIT " + n))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("credits", u.getCredits());
            m.put("gameType", null);
            m.put("roomNo", null);
            m.put("lastActiveTime", u.getLastLoginTime());
            list.add(m);
        }
        return R.ok(list);
    }

    /**
     * 记录/刷新在线玩家快照（客户端进入大厅或牌桌时上报）
     * 供原型 04/11 左侧在线玩家列表使用
     */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat(@RequestParam(required = false) String gameType,
                             @RequestParam(required = false) String roomNo) {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) return R.ok();
        OnlinePlayer p = onlinePlayerMapper.selectOne(new LambdaQueryWrapper<OnlinePlayer>()
                .eq(OnlinePlayer::getUserId, uid));
        if (p == null) {
            p = new OnlinePlayer();
            p.setUserId(uid);
        }
        p.setNickname(u.getNickname());
        p.setAvatar(u.getAvatar());
        p.setCredits(u.getCredits() == null ? 0L : u.getCredits());
        p.setGameType(gameType);
        p.setRoomNo(roomNo);
        p.setLastActiveTime(LocalDateTime.now());
        if (p.getId() == null) onlinePlayerMapper.insert(p);
        else onlinePlayerMapper.updateById(p);
        return R.ok();
    }

    /** 六种游戏入口（原型 04/05：德州、炸金花、抢庄三公、抢庄牛牛、通比牛牛、通比三公） */
    private List<Map<String, Object>> gameEntries() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GameType t : GameType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gameType", t.getCode());
            m.put("name", clientName(t));
            m.put("desc", t.getDesc());
            m.put("icon", t.getCode().toLowerCase());
            list.add(m);
        }
        return list;
    }

    /** 客户端展示名（原型 05/24 使用的名称口径） */
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

    /** 主按钮（原型 04 三个 / 原型 11 四个） */
    private List<Map<String, Object>> mainEntries(boolean isAgent) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(entry("NORMAL", "普通场", "gold", "spade"));
        list.add(entry("HUNDRED", "百人场", "purple", "poker"));
        list.add(entry("TOURNAMENT", "赛事场", "gold", "trophy"));
        if (isAgent) {
            list.add(entry("CREATE_ROOM", "创建房间", "blue", "plus"));
        }
        return list;
    }

    private Map<String, Object> entry(String code, String name, String color, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("color", color);
        m.put("icon", icon);
        return m;
    }
}
