package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单（原型 08-充值页）
 * status: 0-待支付 1-已支付 2-已取消 3-已失败
 */
@TableName("recharge_order")
public class RechargeOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    private Long userId;
    private String username;

    /** 充值金额（元，原型档位：6/30/68/128/328/648） */
    private BigDecimal amountYuan;

    /** 赠送游戏币（原型：60万/300万/680万金币） */
    private Long giftCredits;

    /** 实际到账游戏币 */
    private Long actualCredits;

    /** 支付方式 ALIPAY/WECHAT/BANK（原型：支付宝/微信支付/银行卡） */
    private String payMethod;

    /** 0-待支付 1-已支付 2-已取消 3-已失败 */
    private Integer status;

    private LocalDateTime payTime;

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
    public Long getGiftCredits() { return giftCredits; }
    public void setGiftCredits(Long giftCredits) { this.giftCredits = giftCredits; }
    public Long getActualCredits() { return actualCredits; }
    public void setActualCredits(Long actualCredits) { this.actualCredits = actualCredits; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
