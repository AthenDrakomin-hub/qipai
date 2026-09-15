package com.poker.platform.dto;

import javax.validation.constraints.*;

/**
 * 加入房间请求
 *  —— 精确按房号进入：roomNo 必填（6位数字），roomPassword 选填，buyinCredits 必填
 *  —— 自动匹配：roomNo 留空(null或"")，gameType + roomLevel 必填，buyinCredits 选填（不填用房间等级最低带入）
 *  说明：Bean Validation 不做 @Size 限制，防止空字符串走自动匹配时被拦截；Controller 按不同入口做手动校验。
 */
public class JoinRoomDTO {

    private String roomNo;

    private String roomPassword;

    /** 自动匹配用：游戏类型枚举 code（例：CLASSICAL_TEXAS） */
    private String gameType;
    /** 自动匹配用：房间等级枚举 code（PRIMARY / ADVANCED / PREMIUM） */
    private String roomLevel;

    @Min(value = 0, message = "带入筹码不能为负")
    @Max(value = 2000000, message = "带入筹码过大")
    private Long buyinCredits;

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getRoomPassword() { return roomPassword; }
    public void setRoomPassword(String roomPassword) { this.roomPassword = roomPassword; }
    public Long getBuyinCredits() { return buyinCredits; }
    public void setBuyinCredits(Long buyinCredits) { this.buyinCredits = buyinCredits; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public String getRoomLevel() { return roomLevel; }
    public void setRoomLevel(String roomLevel) { this.roomLevel = roomLevel; }
}
