package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游戏币 变动流水记录
 *  变动类型:1-注册赠送 2-代理增减下线 3-对局输赢 4-平台抽水 5-代理水费扣费
 *          6-总代理分润 7-客服人工调整 8-管理员调整 9-代理赠送玩家 10-补扣欠费
 *          11-系统返佣 12-玩家之间赠送(P2P转账) 13-推广划拨：总代→下级代理 / 代理→下级玩家 / 客服→总代·代理·玩家
 */
@TableName("credit_log")
public class CreditLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标用户ID */
    private Long userId;

    private String username;

    /** 变动类型:1-注册赠送 2-代理增减下线 3-对局输赢 4-平台抽水 5-代理水费扣费
     *  6-总代理分润 7-客服人工调整 8-管理员调整 9-代理赠送玩家 10-补扣欠费
     *  11-系统返佣 12-玩家之间赠送(P2P) 13-推广划拨(总代→代理 / 代理→玩家 / 客服→任一级) */
    private Integer changeType;

    /** 变动数值（正为加，负为减） */
    private Long changeValue;

    /** 变动前余额 */
    private Long beforeValue;

    /** 变动后余额 */
    private Long afterValue;

    /** 关联房间ID */
    private Long roomId;

    private String roomNo;

    /** 关联对局ID */
    private Long roundId;

    /** 操作人用户ID（客服/管理员/代理操作时记录） */
    private Long operatorId;

    private String operatorName;

    /** 操作原因/备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getChangeType() { return changeType; }
    public void setChangeType(Integer changeType) { this.changeType = changeType; }
    public Long getChangeValue() { return changeValue; }
    public void setChangeValue(Long changeValue) { this.changeValue = changeValue; }
    public Long getBeforeValue() { return beforeValue; }
    public void setBeforeValue(Long beforeValue) { this.beforeValue = beforeValue; }
    public Long getAfterValue() { return afterValue; }
    public void setAfterValue(Long afterValue) { this.afterValue = afterValue; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
