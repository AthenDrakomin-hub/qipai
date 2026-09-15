package com.poker.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.poker.platform.enums.UserRole;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账号表
 */
@TableName("sys_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号（唯一） */
    private String username;

    /** 密码（MD5+salt加密） */
    private String password;

    /** 盐值 */
    private String salt;

    /** 安全码（用于二次校验） */
    private String securityCode;

    /** 昵称 */
    private String nickname;

    /** 用户角色 @see UserRole */
    private Integer role;

    /** 筹码积分（玩家/代理通用字段，玩家=可用筹码，代理=信用分） */
    private Long credits;

    /** 头像 */
    private String avatar;

    /** 邀请码（自身邀请码，6位唯一） */
    private String inviteCode;

    /** 上级用户ID（通过邀请码注册绑定） */
    private Long parentId;

    /** 上级邀请码 */
    private String parentInviteCode;

    /** 上级用户名（非数据库字段，列表查询时填充） */
    @TableField(exist = false)
    private String parentUsername;

    /** 是否存在扣费失败记录（代理） */
    @TableField("has_fee_failure")
    private Boolean hasFeeFailure;

    /** 登录IP */
    private String lastLoginIp;

    /** 登录时间 */
    private LocalDateTime lastLoginTime;

    /** 账号状态 0-正常 1-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getSecurityCode() { return securityCode; }
    public void setSecurityCode(String securityCode) { this.securityCode = securityCode; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getRole() { return role; }
    public void setRole(Integer role) { this.role = role; }
    public Long getCredits() { return credits; }
    public void setCredits(Long credits) { this.credits = credits; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentInviteCode() { return parentInviteCode; }
    public void setParentInviteCode(String parentInviteCode) { this.parentInviteCode = parentInviteCode; }

    public String getParentUsername() { return parentUsername; }
    public void setParentUsername(String parentUsername) { this.parentUsername = parentUsername; }
    public Boolean getHasFeeFailure() { return hasFeeFailure; }
    public void setHasFeeFailure(Boolean hasFeeFailure) { this.hasFeeFailure = hasFeeFailure; }
    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }
    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
