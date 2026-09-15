package com.poker.platform.security;

/**
 * 用户请求上下文（ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static class UserInfo {
        private Long userId;
        private String username;
        private Integer role;

        public UserInfo() {}

        public UserInfo(Long userId, String username, Integer role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getRole() { return role; }
        public void setRole(Integer role) { this.role = role; }
    }

    public static void set(Long userId, String username, Integer role) {
        HOLDER.set(new UserInfo(userId, username, role));
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        UserInfo u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getUsername() {
        UserInfo u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    public static Integer getRole() {
        UserInfo u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
