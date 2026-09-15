package com.poker.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台业务配置
 */
@Configuration
@ConfigurationProperties(prefix = "platform")
public class PlatformConfig {

    /** 单局赢家抽水比例（默认3） */
    private Integer rakePercent = 3;

    /** 开房抽佣（水费）——按房主角色：总代理 2% */
    private double waterFeeGeneralAgentPercent = 2.0;

    /** 开房抽佣（水费）——一级代理 1.5% */
    private double waterFeePrimaryAgentPercent = 1.5;

    /** 开房抽佣（水费）——二级代理 1% */
    private double waterFeeSecondaryAgentPercent = 1.0;

    /** 多级推广抽成——代理（二级）分佣 1%（需求文档：1%代理分佣） */
    private double commissionSecondaryPercent = 1.0;

    /** 多级推广抽成——一级代理分佣 1% */
    private double commissionPrimaryPercent = 1.0;

    /** 多级推广抽成——总代理分佣 1%（需求文档：1%总代分佣） */
    private double commissionGeneralAgentPercent = 1.0;

    /** 多级推广抽成兜底——无一级时总代理抽二级 1% */
    private double commissionGeneralAgentFallbackPercent = 1.0;

    /** 多级推广抽成兜底——无二级时一级代理抽玩家 1% */
    private double commissionPrimaryFallbackPercent = 1.0;

    /** 公域匹配：平台总抽 0.3% */
    private double publicMatchTotalRakePercent = 0.3;

    /** 公域匹配：各代理层级各 0.15% 依次分佣 */
    private double publicMatchAgentCommissionPercent = 0.15;

    /** 每房间总局数 */
    private Integer roundsPerRoom = 25;

    /** 玩家默认带入区间 */
    private Integer playerMinBuyin = 200;
    private Integer playerMaxBuyin = 2000;

    /** 房间等级配置 */
    private Map<String, RoomLevelCfg> roomLevels = new HashMap<>();

    public Integer getRakePercent() { return rakePercent; }
    public void setRakePercent(Integer rakePercent) { this.rakePercent = rakePercent; }
    public double getWaterFeeGeneralAgentPercent() { return waterFeeGeneralAgentPercent; }
    public void setWaterFeeGeneralAgentPercent(double waterFeeGeneralAgentPercent) { this.waterFeeGeneralAgentPercent = waterFeeGeneralAgentPercent; }
    public double getWaterFeePrimaryAgentPercent() { return waterFeePrimaryAgentPercent; }
    public void setWaterFeePrimaryAgentPercent(double waterFeePrimaryAgentPercent) { this.waterFeePrimaryAgentPercent = waterFeePrimaryAgentPercent; }
    public double getWaterFeeSecondaryAgentPercent() { return waterFeeSecondaryAgentPercent; }
    public void setWaterFeeSecondaryAgentPercent(double waterFeeSecondaryAgentPercent) { this.waterFeeSecondaryAgentPercent = waterFeeSecondaryAgentPercent; }
    public double getCommissionSecondaryPercent() { return commissionSecondaryPercent; }
    public void setCommissionSecondaryPercent(double commissionSecondaryPercent) { this.commissionSecondaryPercent = commissionSecondaryPercent; }
    public double getCommissionPrimaryPercent() { return commissionPrimaryPercent; }
    public void setCommissionPrimaryPercent(double commissionPrimaryPercent) { this.commissionPrimaryPercent = commissionPrimaryPercent; }
    public double getCommissionGeneralAgentPercent() { return commissionGeneralAgentPercent; }
    public void setCommissionGeneralAgentPercent(double commissionGeneralAgentPercent) { this.commissionGeneralAgentPercent = commissionGeneralAgentPercent; }
    public double getCommissionGeneralAgentFallbackPercent() { return commissionGeneralAgentFallbackPercent; }
    public void setCommissionGeneralAgentFallbackPercent(double commissionGeneralAgentFallbackPercent) { this.commissionGeneralAgentFallbackPercent = commissionGeneralAgentFallbackPercent; }
    public double getCommissionPrimaryFallbackPercent() { return commissionPrimaryFallbackPercent; }
    public void setCommissionPrimaryFallbackPercent(double commissionPrimaryFallbackPercent) { this.commissionPrimaryFallbackPercent = commissionPrimaryFallbackPercent; }
    public double getPublicMatchTotalRakePercent() { return publicMatchTotalRakePercent; }
    public void setPublicMatchTotalRakePercent(double publicMatchTotalRakePercent) { this.publicMatchTotalRakePercent = publicMatchTotalRakePercent; }
    public double getPublicMatchAgentCommissionPercent() { return publicMatchAgentCommissionPercent; }
    public void setPublicMatchAgentCommissionPercent(double publicMatchAgentCommissionPercent) { this.publicMatchAgentCommissionPercent = publicMatchAgentCommissionPercent; }
    public Integer getRoundsPerRoom() { return roundsPerRoom; }
    public void setRoundsPerRoom(Integer roundsPerRoom) { this.roundsPerRoom = roundsPerRoom; }
    public Integer getPlayerMinBuyin() { return playerMinBuyin; }
    public void setPlayerMinBuyin(Integer playerMinBuyin) { this.playerMinBuyin = playerMinBuyin; }
    public Integer getPlayerMaxBuyin() { return playerMaxBuyin; }
    public void setPlayerMaxBuyin(Integer playerMaxBuyin) { this.playerMaxBuyin = playerMaxBuyin; }
    public Map<String, RoomLevelCfg> getRoomLevels() { return roomLevels; }
    public void setRoomLevels(Map<String, RoomLevelCfg> roomLevels) { this.roomLevels = roomLevels; }

    public static class RoomLevelCfg {
        private Integer minBuyin;
        private Integer maxBuyin;
        private Integer creditThreshold;

        public Integer getMinBuyin() { return minBuyin; }
        public void setMinBuyin(Integer minBuyin) { this.minBuyin = minBuyin; }
        public Integer getMaxBuyin() { return maxBuyin; }
        public void setMaxBuyin(Integer maxBuyin) { this.maxBuyin = maxBuyin; }
        public Integer getCreditThreshold() { return creditThreshold; }
        public void setCreditThreshold(Integer creditThreshold) { this.creditThreshold = creditThreshold; }
    }
}
