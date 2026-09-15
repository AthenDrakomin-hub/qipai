package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.entity.FaqItem;
import com.poker.platform.entity.PlatformComplaint;
import com.poker.platform.entity.User;
import com.poker.platform.enums.MsgType;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.FaqItemMapper;
import com.poker.platform.mapper.PlatformComplaintMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.MessageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服 / 投诉（原型 21-客服投诉页）
 *
 * 页面元素映射：
 *  左卡片：客服头像 + 在线标识、「在线客服」、「工作时间 9:00-24:00」、「发起对话」按钮
 *  左下区块「投诉记录」：日期 + 投诉标题 + 处理状态
 *  右卡片「问题描述」：多行文本 + 「添加截图」
 *  「常见问题」折叠面板：如何充值? / 如何提现? / 账号被冻结怎么办?
 *  「提交投诉」按钮
 */
@RestController
@RequestMapping("/cs")
public class CustomerServiceController {

    /** 客服在线服务时间（原型 21「工作时间 9:00-24:00」） */
    private static final String SERVICE_TIME = "9:00-24:00";

    @Resource private PlatformComplaintMapper complaintMapper;
    @Resource private FaqItemMapper faqMapper;
    @Resource private UserMapper userMapper;
    @Resource private MessageService messageService;

    /**
     * 客服页初始化数据
     * 原型 21：客服信息 + 常见问题列表
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("serviceName", "在线客服");
        res.put("serviceTime", SERVICE_TIME);
        res.put("online", true);
        res.put("avatar", null);

        List<FaqItem> faqs = faqMapper.selectList(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getStatus, 1)
                .orderByAsc(FaqItem::getSortNo));
        res.put("faqs", faqs);
        return R.ok(res);
    }

    /**
     * 常见问题列表（原型 21「常见问题」折叠面板）
     * 返回 question（问题） + answer（展开内容）
     */
    @GetMapping("/faqs")
    public R<List<FaqItem>> faqs() {
        return R.ok(faqMapper.selectList(new LambdaQueryWrapper<FaqItem>()
                .eq(FaqItem::getStatus, 1)
                .orderByAsc(FaqItem::getSortNo)));
    }

    /**
     * 发起对话（原型 21「发起对话」按钮）
     * 返回客服会话标识；实时消息走 WebSocket /ws/room/{roomId} 或后续独立的客服通道。
     */
    @PostMapping("/session")
    public R<Map<String, Object>> startSession() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("serviceName", "在线客服");
        res.put("serviceTime", SERVICE_TIME);
        res.put("sessionId", "CS" + UserContext.getUserId() + "-" + System.currentTimeMillis());
        res.put("tip", "客服将在 " + SERVICE_TIME + " 内为您服务，请耐心等待");
        return R.ok(res);
    }

    /**
     * 提交投诉（原型 21「提交投诉」按钮 + 「问题描述」文本域 + 「添加截图」）
     *
     * @param body complaintType-投诉类型、title-标题、content-问题描述、images-截图（逗号分隔）
     */
    @PostMapping("/complaint")
    public R<PlatformComplaint> submit(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException("用户不存在");

        String content = str(body.get("content"));
        if (content == null || content.trim().isEmpty()) {
            throw new BizException("请填写问题描述");
        }
        if (content.trim().length() < 5) {
            throw new BizException("问题描述至少5个字，请详细说明");
        }

        PlatformComplaint c = new PlatformComplaint();
        c.setUserId(uid);
        c.setUsername(u.getUsername());
        Object type = body.get("complaintType");
        c.setComplaintType(type == null ? 5 : Integer.valueOf(String.valueOf(type)));
        String title = str(body.get("title"));
        c.setTitle(title == null || title.trim().isEmpty()
                ? content.trim().substring(0, Math.min(20, content.trim().length()))
                : title.trim());
        c.setContent(content.trim());
        c.setImages(str(body.get("images")));
        c.setStatus(0); // 待处理
        complaintMapper.insert(c);

        // 提交后回执消息（原型 20「投诉处理」消息的起点）
        messageService.send(uid, MsgType.COMPLAINT.getCode(), "投诉已提交",
                "您提交的投诉已受理，客服将在24小时内处理，请留意消息通知", c.getId());
        return R.ok("投诉已提交，客服会尽快处理", c);
    }

    /**
     * 我的投诉记录（原型 21「投诉记录」）
     * 返回：日期、投诉标题、处理状态
     */
    @GetMapping("/complaints")
    public R<Page<PlatformComplaint>> myComplaints(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size) {
        Long uid = UserContext.getUserId();
        return R.ok(complaintMapper.selectPage(
                new Page<>(page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : Math.min(size, 100)),
                new LambdaQueryWrapper<PlatformComplaint>()
                        .eq(PlatformComplaint::getUserId, uid)
                        .orderByDesc(PlatformComplaint::getId)));
    }

    /**
     * 投诉详情
     */
    @GetMapping("/complaint/{id}")
    public R<PlatformComplaint> detail(@PathVariable Long id) {
        PlatformComplaint c = complaintMapper.selectById(id);
        if (c == null) throw new BizException("投诉记录不存在");
        if (!c.getUserId().equals(UserContext.getUserId())) throw new BizException("无权查看该投诉");
        return R.ok(c);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
