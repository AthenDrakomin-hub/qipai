package com.poker.platform.dto;

import javax.validation.constraints.*;

/**
 * 创建房间请求
 */
public class CreateRoomDTO {

    @NotBlank(message = "游戏类型不能为空")
    private String gameType;

    @NotBlank(message = "房间等级不能为空")
    private String roomLevel;

    private String roomName;

    private String roomPassword;

    @Min(value = 2, message = "最少2位玩家")
    @Max(value = 10, message = "最多10位玩家")
    private Integer maxPlayers = 8;

    /** 通比类游戏的统一下注额 */
    private Long fixedBetAmount = 100L;

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public String getRoomLevel() { return roomLevel; }
    public void setRoomLevel(String roomLevel) { this.roomLevel = roomLevel; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomPassword() { return roomPassword; }
    public void setRoomPassword(String roomPassword) { this.roomPassword = roomPassword; }
    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }
    public Long getFixedBetAmount() { return fixedBetAmount; }
    public void setFixedBetAmount(Long fixedBetAmount) { this.fixedBetAmount = fixedBetAmount; }
}
