package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单局对局记录表
 */
@TableName("game_round")
public class GameRound implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String roomNo;

    /** 第几局 */
    private Integer roundNo;

    /** 本局总流水（下注总额） */
    private Long roundTurnover;

    /** 本局平台抽水 */
    private Long roundRake;

    /** 庄家用户ID（抢庄模式） */
    private Long bankerId;

    /** 赢家用户ID列表，逗号分隔 */
    private String winnerIds;

    /** 输家用户ID列表，逗号分隔 */
    private String loserIds;

    /** 本局详细结算JSON（玩家ID->输赢明细） */
    private String settlementDetail;

    /** 发牌记录JSON（仅用于审计，不回传给前端底牌） */
    private String dealRecord;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Long getRoundTurnover() { return roundTurnover; }
    public void setRoundTurnover(Long roundTurnover) { this.roundTurnover = roundTurnover; }
    public Long getRoundRake() { return roundRake; }
    public void setRoundRake(Long roundRake) { this.roundRake = roundRake; }
    public Long getBankerId() { return bankerId; }
    public void setBankerId(Long bankerId) { this.bankerId = bankerId; }
    public String getWinnerIds() { return winnerIds; }
    public void setWinnerIds(String winnerIds) { this.winnerIds = winnerIds; }
    public String getLoserIds() { return loserIds; }
    public void setLoserIds(String loserIds) { this.loserIds = loserIds; }
    public String getSettlementDetail() { return settlementDetail; }
    public void setSettlementDetail(String settlementDetail) { this.settlementDetail = settlementDetail; }
    public String getDealRecord() { return dealRecord; }
    public void setDealRecord(String dealRecord) { this.dealRecord = dealRecord; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
