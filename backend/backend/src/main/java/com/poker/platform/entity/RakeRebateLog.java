package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 返佣历史账单（房主扣款+系统返佣记录）
 * 显示字段：房间号、扣除多少、系统返还信用分
 */
@TableName("rake_rebate_log")
public class RakeRebateLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房主用户ID（总代理或二级代理） */
    private Long ownerId;

    private String ownerName;

    /** 房主角色（2-二级代理 3-总代理） */
    private Integer ownerRole;

    /** 房间ID */
    private Long roomId;

    /** 房间号（账单展示核心字段） */
    private String roomNo;

    /** 房间总流水 */
    private Long roomTurnover;

    /** 房间总抽水 */
    private Long totalRake;

    /** 扣除信用分（按总流水3%等值扣除 = 与玩家抽水1:1） */
    private Long deductedAmount;

    /** 系统返还信用分（总流水1%自动返佣） */
    private Long rebateAmount;

    /** 房主净成本 = deductedAmount - rebateAmount（通常=总流水2%） */
    private Long netCost;

    /** 扣款前信用分 */
    private Long beforeCredit;

    /** 扣款+返佣后最终信用分 */
    private Long afterCredit;

    /** 状态 0-成功 1-失败（信用分不足） */
    private Integer status;

    /** 失败原因 */
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ====== getters/setters ======
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Integer getOwnerRole() { return ownerRole; }
    public void setOwnerRole(Integer ownerRole) { this.ownerRole = ownerRole; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getRoomTurnover() { return roomTurnover; }
    public void setRoomTurnover(Long roomTurnover) { this.roomTurnover = roomTurnover; }
    public Long getTotalRake() { return totalRake; }
    public void setTotalRake(Long totalRake) { this.totalRake = totalRake; }
    public Long getDeductedAmount() { return deductedAmount; }
    public void setDeductedAmount(Long deductedAmount) { this.deductedAmount = deductedAmount; }
    public Long getRebateAmount() { return rebateAmount; }
    public void setRebateAmount(Long rebateAmount) { this.rebateAmount = rebateAmount; }
    public Long getNetCost() { return netCost; }
    public void setNetCost(Long netCost) { this.netCost = netCost; }
    public Long getBeforeCredit() { return beforeCredit; }
    public void setBeforeCredit(Long beforeCredit) { this.beforeCredit = beforeCredit; }
    public Long getAfterCredit() { return afterCredit; }
    public void setAfterCredit(Long afterCredit) { this.afterCredit = afterCredit; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
