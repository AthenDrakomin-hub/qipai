package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 总代理分润记录
 */
@TableName("general_agent_commission")
public class GeneralAgentCommission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 总代理用户ID */
    private Long generalAgentId;

    private String generalAgentName;

    /** 来源二级代理ID */
    private Long sourceAgentId;

    private String sourceAgentName;

    /** 来源房间ID */
    private Long roomId;

    private String roomNo;

    /** 房间总流水 */
    private Long roomTurnover;

    /** 分润金额（总流水 * 1%） */
    private Long commissionAmount;

    /** 分润前信用分 */
    private Long beforeCredit;

    /** 分润后信用分 */
    private Long afterCredit;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGeneralAgentId() { return generalAgentId; }
    public void setGeneralAgentId(Long generalAgentId) { this.generalAgentId = generalAgentId; }
    public String getGeneralAgentName() { return generalAgentName; }
    public void setGeneralAgentName(String generalAgentName) { this.generalAgentName = generalAgentName; }
    public Long getSourceAgentId() { return sourceAgentId; }
    public void setSourceAgentId(Long sourceAgentId) { this.sourceAgentId = sourceAgentId; }
    public String getSourceAgentName() { return sourceAgentName; }
    public void setSourceAgentName(String sourceAgentName) { this.sourceAgentName = sourceAgentName; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getRoomTurnover() { return roomTurnover; }
    public void setRoomTurnover(Long roomTurnover) { this.roomTurnover = roomTurnover; }
    public Long getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(Long commissionAmount) { this.commissionAmount = commissionAmount; }
    public Long getBeforeCredit() { return beforeCredit; }
    public void setBeforeCredit(Long beforeCredit) { this.beforeCredit = beforeCredit; }
    public Long getAfterCredit() { return afterCredit; }
    public void setAfterCredit(Long afterCredit) { this.afterCredit = afterCredit; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
