package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.dto.R;
import com.poker.platform.entity.User;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邀请好友（原型 22-邀请好友页）
 *
 * 页面元素映射：
 *  ◆ 我的邀请码 ◆ VPK88888 + 复制按钮
 *  ◆ 邀请链接 ◆ https://v-poker.com/r/VPK88888 + 复制按钮
 *  「分享给好友」按钮
 *  「已邀请好友（12人）」列表：玩家、日期、状态（已充值/已注册/待充值）、注册时间、奖励按钮
 *
 * 说明：与旧接口 /user/agent/subordinates 的区别——本接口面向**所有角色**（玩家也可查看自己的直推下线），
 *      且以「邀请」语义组织字段（邀请日期、充值状态、奖励状态）。
 */
@RestController
@RequestMapping("/invite")
public class InviteController {

    @Resource
    private UserMapper userMapper;

    @Value("${platform.invite-base-url:https://v-poker.com/r/}")
    private String inviteBaseUrl;

    /**
     * 邀请页初始化
     * 原型 22：我的邀请码 + 邀请链接 + 已邀请人数
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Long uid = UserContext.getUserId();
        User me = userMapper.selectById(uid);
        if (me == null) throw new BizException("用户不存在");

        long invitedCount = countChildren(uid);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("inviteCode", me.getInviteCode());
        res.put("inviteLink", buildLink(me.getInviteCode()));
        res.put("invitedCount", invitedCount);
        res.put("canInvite", canInvite(me.getRole()));
        res.put("shareTitle", "V-POKER 龙腾竞技平台，注册即送百万金币");
        res.put("shareDesc", "使用我的邀请码 " + me.getInviteCode() + " 注册，一起开局！");
        return R.ok(res);
    }

    /**
     * 我的邀请码与邀请链接
     * 原型 22「◆ 我的邀请码 ◆」「◆ 邀请链接 ◆」
     */
    @GetMapping("/code")
    public R<Map<String, Object>> code() {
        Long uid = UserContext.getUserId();
        User me = userMapper.selectById(uid);
        if (me == null) throw new BizException("用户不存在");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("inviteCode", me.getInviteCode());
        res.put("inviteLink", buildLink(me.getInviteCode()));
        return R.ok(res);
    }

    /**
     * 邀请记录列表
     * 原型 22「已邀请好友」列表：玩家、日期、状态、注册时间、奖励状态
     *
     * @param status 筛选：ALL(默认) / RECHARGED-已充值 / REGISTERED-已注册 / PENDING-待充值
     */
    @GetMapping("/records")
    public R<Map<String, Object>> records(@RequestParam(required = false) String status) {
        Long uid = UserContext.getUserId();
        List<User> children = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getParentId, uid)
                .orderByDesc(User::getCreateTime));

        List<Map<String, Object>> records = new ArrayList<>();
        for (User c : children) {
            // 是否已充值：以该用户产生的充值单为准，这里用「游戏币 > 0」作为近似判据
            boolean recharged = c.getCredits() != null && c.getCredits() > 0;
            String st = recharged ? "RECHARGED" : "REGISTERED";

            if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
                if ("RECHARGED".equalsIgnoreCase(status) && !recharged) continue;
                if ("REGISTERED".equalsIgnoreCase(status) && recharged) continue;
                if ("PENDING".equalsIgnoreCase(status)) continue; // 待充值由「已注册未充值」表达
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", c.getId());
            m.put("nickname", c.getNickname());
            m.put("username", c.getUsername());
            m.put("avatar", c.getAvatar());
            m.put("role", c.getRole());
            UserRole r = UserRole.fromCode(c.getRole());
            m.put("roleDesc", r == null ? "" : r.getDesc());
            m.put("credits", c.getCredits());
            m.put("inviteDate", c.getCreateTime());      // 原型「日期」
            m.put("registerTime", c.getCreateTime());    // 原型「注册时间」
            m.put("status", st);
            m.put("statusDesc", recharged ? "已充值" : "已注册");
            m.put("rewarded", recharged);
            m.put("rewardDesc", recharged ? "奖励已发放" : "待充值");
            records.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", children.size());
        res.put("records", records);
        return R.ok(res);
    }

    private long countChildren(Long uid) {
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getParentId, uid));
        return c == null ? 0L : c;
    }

    private String buildLink(String inviteCode) {
        String base = inviteBaseUrl == null ? "https://v-poker.com/r/" : inviteBaseUrl;
        return base + (inviteCode == null ? "" : inviteCode);
    }

    /** 是否具备发展下线资格（玩家不可发展下线，注册时后端会拒绝） */
    private boolean canInvite(Integer role) {
        if (role == null) return false;
        return role == 2 || role == 3 || role == 5 || role == 6;
    }
}
