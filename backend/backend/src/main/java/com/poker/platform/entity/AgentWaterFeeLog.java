package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理水费扣费记录
 */
@TableName("agent_water_fee_log")
public class AgentWaterFeeLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 代理用户ID */
    private Long agentId;

    private String agentName;

    private Long roomId;

    private String roomNo;

    /** 房间总流水 */
    private Long roomTurnover;

    /** 扣费金额（总流水2%） */
    private Long feeAmount;

    /** 扣费前信用分 */
    private Long beforeCredit;

    /** 扣费后信用分 */
    private Long afterCredit;

    /** 状态：0-成功 1-失败（信用分不足） */
    private Integer status;

    /** 失败原因 */
    private String failReason;

    /** 是否已补扣 */
    private Boolean repaid;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getRoomTurnover() { return roomTurnover; }
    public void setRoomTurnover(Long roomTurnover) { this.roomTurnover = roomTurnover; }
    public Long getFeeAmount() { return feeAmount; }
    public void setFeeAmount(Long feeAmount) { this.feeAmount = feeAmount; }
    public Long getBeforeCredit() { return beforeCredit; }
    public void setBeforeCredit(Long beforeCredit) { this.beforeCredit = beforeCredit; }
    public Long getAfterCredit() { return afterCredit; }
    public void setAfterCredit(Long afterCredit) { this.afterCredit = afterCredit; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Boolean getRepaid() { return repaid; }
    public void setRepaid(Boolean repaid) { this.repaid = repaid; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
