package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.entity.UserMessage;
import com.poker.platform.enums.MsgType;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.UserMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息通知服务（原型 20-消息通知页 / 04-大厅铃铛红点）
 *
 * 消息来源：
 *  - 系统消息：注册成功、登录提醒、版本更新、比赛邀请
 *  - 佣金消息：代理佣金结算到账
 *  - 投诉消息：投诉被处理
 *  - 充值消息：充值到账
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    @Resource
    private UserMessageMapper messageMapper;

    /**
     * 发送消息（内部调用，供其他业务模块触发）
     *
     * @param userId  接收者
     * @param msgType 消息类型 @see MsgType
     * @param title   标题
     * @param content 内容
     * @param bizId   关联业务ID（可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void send(Long userId, Integer msgType, String title, String content, Long bizId) {
        if (userId == null) return;
        UserMessage m = new UserMessage();
        m.setUserId(userId);
        m.setMsgType(msgType);
        m.setTitle(title);
        m.setContent(content);
        m.setBizId(bizId);
        m.setReadFlag(false);
        messageMapper.insert(m);
        log.info("发送消息 uid={} type={} title={}", userId, msgType, title);
    }

    /**
     * 消息列表（原型 20「全部 / 系统 / 佣金 / 投诉」Tab 筛选 + 分页）
     *
     * @param userId 当前用户
     * @param tab    筛选 Tab：ALL/SYSTEM/COMMISSION/COMPLAINT（null 或 ALL 表示全部）
     * @param page   页码
     * @param size   页大小
     */
    public Page<UserMessage> list(Long userId, String tab, Integer page, Integer size) {
        LambdaQueryWrapper<UserMessage> q = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId);

        Integer msgType = MsgType.fromTab(tab);
        if (msgType != null) {
            q.eq(UserMessage::getMsgType, msgType);
        }
        q.orderByDesc(UserMessage::getCreateTime).orderByDesc(UserMessage::getId);

        return messageMapper.selectPage(new Page<>(page == null || page < 1 ? 1 : page,
                size == null || size < 1 ? 10 : Math.min(size, 100)), q);
    }

    /**
     * 未读消息统计（原型 20 红点 / 原型 04 大厅铃铛红点）
     * 返回 total 与按类型分组的 counts
     */
    public Map<String, Object> unreadCount(Long userId) {
        Map<String, Object> res = new LinkedHashMap<>();
        long total = countUnread(userId, null);
        res.put("total", total);
        Map<String, Long> byType = new LinkedHashMap<>();
        for (MsgType t : MsgType.values()) {
            byType.put(t.name(), countUnread(userId, t.getCode()));
        }
        res.put("byType", byType);
        return res;
    }

    private long countUnread(Long userId, Integer msgType) {
        LambdaQueryWrapper<UserMessage> q = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getReadFlag, false);
        if (msgType != null) q.eq(UserMessage::getMsgType, msgType);
        Long c = messageMapper.selectCount(q);
        return c == null ? 0L : c;
    }

    /** 标记单条已读 */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long messageId) {
        UserMessage m = messageMapper.selectById(messageId);
        if (m == null) throw new BizException("消息不存在");
        if (!m.getUserId().equals(userId)) throw new BizException("无权操作该消息");
        if (Boolean.TRUE.equals(m.getReadFlag())) return;
        m.setReadFlag(true);
        m.setReadTime(LocalDateTime.now());
        messageMapper.updateById(m);
    }

    /** 全部标记已读（可按 Tab 限定范围） */
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId, String tab) {
        LambdaQueryWrapper<UserMessage> q = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getReadFlag, false);
        Integer msgType = MsgType.fromTab(tab);
        if (msgType != null) q.eq(UserMessage::getMsgType, msgType);

        UserMessage upd = new UserMessage();
        upd.setReadFlag(true);
        upd.setReadTime(LocalDateTime.now());
        return messageMapper.update(upd, q);
    }

    /** 删除消息（逻辑删除） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long messageId) {
        UserMessage m = messageMapper.selectById(messageId);
        if (m == null) throw new BizException("消息不存在");
        if (!m.getUserId().equals(userId)) throw new BizException("无权操作该消息");
        messageMapper.deleteById(messageId);
    }
}
