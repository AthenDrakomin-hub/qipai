package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现订单（原型 17-提现页）
 * status: 0-待处理 1-已到账 2-已驳回
 */
@TableName("withdraw_order")
public class WithdrawOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    private Long userId;
    private String username;

    /** 提现金额（元） */
    private BigDecimal amountYuan;

    /** 扣减游戏币 */
    private Long deductCredits;

    /** 收款方式 ALIPAY/WECHAT/BANK */
    private String withdrawMethod;

    /** 收款账号 */
    private String account;

    /** 0-待处理 1-已到账 2-已驳回 */
    private Integer status;

    private String failReason;

    private Long auditId;
    private String auditName;
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public BigDecimal getAmountYuan() { return amountYuan; }
    public void setAmountYuan(BigDecimal amountYuan) { this.amountYuan = amountYuan; }
    public Long getDeductCredits() { return deductCredits; }
    public void setDeductCredits(Long deductCredits) { this.deductCredits = deductCredits; }
    public String getWithdrawMethod() { return withdrawMethod; }
    public void setWithdrawMethod(String withdrawMethod) { this.withdrawMethod = withdrawMethod; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public String getAuditName() { return auditName; }
    public void setAuditName(String auditName) { this.auditName = auditName; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
