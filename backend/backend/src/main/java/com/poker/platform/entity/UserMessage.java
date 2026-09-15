package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户消息通知（原型 20-消息通知页）
 * msg_type: 1-系统 2-佣金 3-投诉 4-充值 5-活动
 */
@TableName("user_message")
public class UserMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者用户ID */
    private Long userId;

    /** 消息类型 1-系统 2-佣金 3-投诉 4-充值 5-活动 */
    private Integer msgType;

    /** 标题（原型：系统通知 / 充值成功 / 佣金到账 / 比赛邀请 / 投诉处理 / 版本更新） */
    private String title;

    /** 内容摘要 */
    private String content;

    /** 关联业务ID（投诉单/提现单等） */
    private Long bizId;

    /** 是否已读 0-未读 1-已读（原型：红点/灰点） */
    private Boolean readFlag;

    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getMsgType() { return msgType; }
    public void setMsgType(Integer msgType) { this.msgType = msgType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Boolean getReadFlag() { return readFlag; }
    public void setReadFlag(Boolean readFlag) { this.readFlag = readFlag; }
    public LocalDateTime getReadTime() { return readTime; }
    public void setReadTime(LocalDateTime readTime) { this.readTime = readTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
