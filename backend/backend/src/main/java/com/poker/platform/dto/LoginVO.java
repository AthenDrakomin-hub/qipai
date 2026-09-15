package com.poker.platform.dto;

/**
 * 登录响应
 */
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private Integer role;
    private String roleDesc;
    private Long credits;
    private String inviteCode;
    private String parentInviteCode;
    private Boolean hasFeeFailure;
    private String avatar;

    public LoginVO() {}

    public LoginVO(String token, Long userId, String username, String nickname, Integer role,
                   String roleDesc, Long credits, String inviteCode, String parentInviteCode,
                   Boolean hasFeeFailure, String avatar) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.roleDesc = roleDesc;
        this.credits = credits;
        this.inviteCode = inviteCode;
        this.parentInviteCode = parentInviteCode;
        this.hasFeeFailure = hasFeeFailure;
        this.avatar = avatar;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getRole() { return role; }
    public void setRole(Integer role) { this.role = role; }
    public String getRoleDesc() { return roleDesc; }
    public void setRoleDesc(String roleDesc) { this.roleDesc = roleDesc; }
    public Long getCredits() { return credits; }
    public void setCredits(Long credits) { this.credits = credits; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public String getParentInviteCode() { return parentInviteCode; }
    public void setParentInviteCode(String parentInviteCode) { this.parentInviteCode = parentInviteCode; }
    public Boolean getHasFeeFailure() { return hasFeeFailure; }
    public void setHasFeeFailure(Boolean hasFeeFailure) { this.hasFeeFailure = hasFeeFailure; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
