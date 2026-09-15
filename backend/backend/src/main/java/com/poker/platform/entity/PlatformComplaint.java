package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台投诉/工单
 */
@TableName("platform_complaint")
public class PlatformComplaint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交人用户ID */
    private Long userId;

    private String username;

    /** 投诉类型：1-账号问题 2-对局问题 3-代理问题 4-系统问题 5-其他 */
    private Integer complaintType;

    /** 标题 */
    private String title;

    /** 详细内容 */
    private String content;

    /** 图片附件URL，逗号分隔 */
    private String images;

    /** 处理状态 0-待处理 1-处理中 2-已处理 3-已驳回 */
    private Integer status;

    /** 处理人ID */
    private Long handlerId;

    private String handlerName;

    /** 处理回复 */
    private String reply;

    /** 处理时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getComplaintType() { return complaintType; }
    public void setComplaintType(Integer complaintType) { this.complaintType = complaintType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
