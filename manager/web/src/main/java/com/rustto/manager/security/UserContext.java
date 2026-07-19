package com.rustto.manager.security;

/**
 * 当前请求用户上下文（ThreadLocal）。由 {@link TokenInterceptor} 填充。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    /**
     * 设置当前用户。
     *
     * @param userId   用户 ID
     * @param username 用户名
     */
    public static void set(Long userId, String username) {
        HOLDER.set(new CurrentUser(userId, username));
    }

    /**
     * 获取当前用户。
     *
     * @return 当前用户，未登录返回 null
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 清理（请求结束务必调用，避免线程池线程复用导致的串号）。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户信息。 */
    public static class CurrentUser {
        /** 用户 ID。 */
        public final Long userId;
        /** 用户名。 */
        public final String username;

        /**
         * @param userId   用户 ID
         * @param username 用户名
         */
        public CurrentUser(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }
}
