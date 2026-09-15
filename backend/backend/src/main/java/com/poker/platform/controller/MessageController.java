package com.poker.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.entity.UserMessage;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.MessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 消息通知（原型 20-消息通知页）
 *
 * 页面元素映射：
 *  顶部标题「消息通知」+ 返回箭头
 *  Tab 栏：全部 / 系统 / 佣金 / 投诉
 *  消息列表：标题、内容摘要、时间、未读红点
 *  空状态：暂无消息
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    /**
     * 消息列表（Tab 筛选 + 分页）
     * 原型 20 消息列表
     *
     * @param tab  全部(不传/ALL) / 系统(SYSTEM) / 佣金(COMMISSION) / 投诉(COMPLAINT)
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     */
    @GetMapping("/list")
    public R<Page<UserMessage>> list(@RequestParam(required = false) String tab,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer size) {
        return R.ok(messageService.list(UserContext.getUserId(), tab, page, size));
    }

    /**
     * 未读消息数
     * 原型 20 未读红点 / 原型 04、11 大厅铃铛红点
     * 返回：total（总未读）+ byType（按类型分组未读）
     */
    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(messageService.unreadCount(UserContext.getUserId()));
    }

    /**
     * 标记单条已读
     * 原型 20：点击消息后红点变灰点
     */
    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(UserContext.getUserId(), id);
        return R.ok();
    }

    /**
     * 全部标记已读（可按 Tab 限定）
     * 原型 20：进入页面后可一键清除当前 Tab 红点
     */
    @PostMapping("/read-all")
    public R<Integer> markAllRead(@RequestParam(required = false) String tab) {
        return R.ok(messageService.markAllRead(UserContext.getUserId(), tab));
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        messageService.delete(UserContext.getUserId(), id);
        return R.ok();
    }
}
