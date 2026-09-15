package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户设置（原型 23-设置页 / 45-牌桌设置面板）
 */
@TableName("user_setting")
public class UserSetting implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 音效开关 */
    private Boolean soundEnabled;

    /** 背景音乐开关 */
    private Boolean musicEnabled;

    /** 震动开关 */
    private Boolean vibrateEnabled;

    /** 自动挂机开关 */
    private Boolean autoPlayEnabled;

    /** 显示快捷下注按钮（原型 45「快速操作 • 显示快捷下注按钮」） */
    private Boolean quickBetEnabled;

    /** 语言，默认 zh-CN（原型：简体中文） */
    private String language;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(Boolean soundEnabled) { this.soundEnabled = soundEnabled; }
    public Boolean getMusicEnabled() { return musicEnabled; }
    public void setMusicEnabled(Boolean musicEnabled) { this.musicEnabled = musicEnabled; }
    public Boolean getVibrateEnabled() { return vibrateEnabled; }
    public void setVibrateEnabled(Boolean vibrateEnabled) { this.vibrateEnabled = vibrateEnabled; }
    public Boolean getAutoPlayEnabled() { return autoPlayEnabled; }
    public void setAutoPlayEnabled(Boolean autoPlayEnabled) { this.autoPlayEnabled = autoPlayEnabled; }
    public Boolean getQuickBetEnabled() { return quickBetEnabled; }
    public void setQuickBetEnabled(Boolean quickBetEnabled) { this.quickBetEnabled = quickBetEnabled; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
