package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 房间表
 */
@TableName("game_room")
public class GameRoom implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间号（6位数字唯一） */
    private String roomNo;

    /** 房间密码 */
    private String roomPassword;

    /** 游戏类型 */
    private String gameType;

    /** 房间等级 */
    private String roomLevel;

    /** 房间名称 */
    private String roomName;

    /** 房主（二级代理用户ID） */
    private Long ownerId;

    /** 房主邀请码 */
    private String ownerInviteCode;

    /** 最大玩家数 */
    private Integer maxPlayers;

    /** 最低带入筹码 */
    private Integer minBuyin;

    /** 最高带入筹码 */
    private Integer maxBuyin;

    /** 房间状态 0-等待中 1-对局中 2-已结算 3-已关闭 */
    private Integer status;

    /** 当前进行到第几局 */
    private Integer currentRound;

    /** 总对局数（固定25局） */
    private Integer totalRounds;

    /** 房间累计总流水 */
    private Long totalTurnover;

    /** 房间累计平台抽水总额 */
    private Long totalRake;

    /** 房间累计代理水费总额 */
    private Long totalWaterFee;

    /** 统一下注额（通比类游戏使用） */
    private Long fixedBetAmount;

    /** 是否为公域匹配房（系统匹配房：无房主，结算按 平台总抽0.3% + 各代理0.15% 分佣） */
    private Boolean isPublicMatch;

    /** 公域匹配房关联的归属代理ID（玩家首次匹配时绑定的上级代理，用于结算分佣） */
    private Long matchAgentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getRoomPassword() { return roomPassword; }
    public void setRoomPassword(String roomPassword) { this.roomPassword = roomPassword; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public String getRoomLevel() { return roomLevel; }
    public void setRoomLevel(String roomLevel) { this.roomLevel = roomLevel; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerInviteCode() { return ownerInviteCode; }
    public void setOwnerInviteCode(String ownerInviteCode) { this.ownerInviteCode = ownerInviteCode; }
    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
    public Integer getMinBuyin() { return minBuyin; }
    public void setMinBuyin(Integer minBuyin) { this.minBuyin = minBuyin; }
    public Integer getMaxBuyin() { return maxBuyin; }
    public void setMaxBuyin(Integer maxBuyin) { this.maxBuyin = maxBuyin; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }
    public Integer getTotalRounds() { return totalRounds; }
    public void setTotalRounds(Integer totalRounds) { this.totalRounds = totalRounds; }
    public Long getTotalTurnover() { return totalTurnover; }
    public void setTotalTurnover(Long totalTurnover) { this.totalTurnover = totalTurnover; }
    public Long getTotalRake() { return totalRake; }
    public void setTotalRake(Long totalRake) { this.totalRake = totalRake; }
    public Long getTotalWaterFee() { return totalWaterFee; }
    public void setTotalWaterFee(Long totalWaterFee) { this.totalWaterFee = totalWaterFee; }
    public Long getFixedBetAmount() { return fixedBetAmount; }
    public void setFixedBetAmount(Long fixedBetAmount) { this.fixedBetAmount = fixedBetAmount; }
    public Boolean getIsPublicMatch() { return isPublicMatch; }
    public void setIsPublicMatch(Boolean isPublicMatch) { this.isPublicMatch = isPublicMatch; }
    public Long getMatchAgentId() { return matchAgentId; }
    public void setMatchAgentId(Long matchAgentId) { this.matchAgentId = matchAgentId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
