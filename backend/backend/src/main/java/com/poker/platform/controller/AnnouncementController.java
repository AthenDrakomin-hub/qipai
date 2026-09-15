package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.dto.R;
import com.poker.platform.entity.PlatformAnnouncement;
import com.poker.platform.mapper.PlatformAnnouncementMapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 平台公告（原型 04/11 大厅公告滚动条、原型 20 系统消息来源）
 *
 * 说明：原 /admin/announcements 需超管权限，客户端无法读取；
 *      本控制器提供**只读**接口，供大厅公告滚动条与公告详情使用。
 */
@RestController
@RequestMapping("/announcement")
public class AnnouncementController {

    @Resource
    private PlatformAnnouncementMapper announcementMapper;

    /**
     * 公告列表（按置顶 + 时间倒序）
     * 原型 04/11：顶部公告滚动条「欢迎来到V-POKER 新用户注册送百万金币」
     * 原型 04/11：公告条为两级显示（小条 + 大条），前端取前 N 条即可
     */
    @GetMapping("/list")
    public R<List<PlatformAnnouncement>> list(@RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<PlatformAnnouncement> q = new LambdaQueryWrapper<PlatformAnnouncement>()
                .eq(PlatformAnnouncement::getStatus, 1)
                .orderByDesc(PlatformAnnouncement::getTopFlag)
                .orderByDesc(PlatformAnnouncement::getCreateTime);
        if (limit != null && limit > 0) {
            q.last("LIMIT " + Math.min(limit, 50));
        }
        return R.ok(announcementMapper.selectList(q));
    }

    /**
     * 置顶公告（大厅滚动条专用，仅返回置顶项）
     */
    @GetMapping("/top")
    public R<List<PlatformAnnouncement>> top() {
        return R.ok(announcementMapper.selectList(new LambdaQueryWrapper<PlatformAnnouncement>()
                .eq(PlatformAnnouncement::getStatus, 1)
                .eq(PlatformAnnouncement::getTopFlag, true)
                .orderByDesc(PlatformAnnouncement::getCreateTime)));
    }

    /**
     * 公告详情
     */
    @GetMapping("/{id}")
    public R<PlatformAnnouncement> detail(@PathVariable Long id) {
        return R.ok(announcementMapper.selectById(id));
    }
}
