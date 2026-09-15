package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 房间玩家记录表（每房间每玩家带入筹码、每局结算）
 */
@TableName("room_player")
public class RoomPlayer implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String roomNo;

    private Long userId;

    private String username;

    private String nickname;

    /** 座位号 -1表示观众（代理旁观） */
    private Integer seatNo;

    /** 是否为旁观者 */
    private Boolean isObserver;

    /** 带入筹码积分 */
    private Long buyinCredits;

    /** 当前剩余筹码 */
    private Long currentCredits;

    /** 本房间总输赢（正为赢，负为输） */
    private Long totalProfit;

    /** 是否自动挂机（通比类游戏：开启后自动下注参与每局） */
    private Boolean isAutoPlay;

    /** 金花：是否已看牌（false=闷牌，闷牌下注按 blindBet；true=看牌按 lookBet，且看牌后闷牌方需双倍跟注） */
    private Boolean isLooked;

    /** 是否已准备（原型 30-等待开局：准备按钮 / 已准备-未准备状态） */
    private Boolean isReady;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;

    private LocalDateTime leaveTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getSeatNo() { return seatNo; }
    public void setSeatNo(Integer seatNo) { this.seatNo = seatNo; }
    public Boolean getIsObserver() { return isObserver; }
    public void setIsObserver(Boolean isObserver) { this.isObserver = isObserver; }
    public Long getBuyinCredits() { return buyinCredits; }
    public void setBuyinCredits(Long buyinCredits) { this.buyinCredits = buyinCredits; }
    public Long getCurrentCredits() { return currentCredits; }
    public void setCurrentCredits(Long currentCredits) { this.currentCredits = currentCredits; }
    public Long getTotalProfit() { return totalProfit; }
    public void setTotalProfit(Long totalProfit) { this.totalProfit = totalProfit; }
    public Boolean getIsAutoPlay() { return isAutoPlay; }
    public void setIsAutoPlay(Boolean isAutoPlay) { this.isAutoPlay = isAutoPlay; }
    public Boolean getIsLooked() { return isLooked; }
    public void setIsLooked(Boolean isLooked) { this.isLooked = isLooked; }
    public Boolean getIsReady() { return isReady; }
    public void setIsReady(Boolean isReady) { this.isReady = isReady; }
    public LocalDateTime getJoinTime() { return joinTime; }
    public void setJoinTime(LocalDateTime joinTime) { this.joinTime = joinTime; }
    public LocalDateTime getLeaveTime() { return leaveTime; }
    public void setLeaveTime(LocalDateTime leaveTime) { this.leaveTime = leaveTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
