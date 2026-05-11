package com.example.ragbilibili.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionManager {

    public String login(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    public void logout() {
        StpUtil.logout();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * <p>该方法具有强制登录语义：未登录时会抛出 NotLoginException，
     * 由 GlobalExceptionHandler 统一映射为 NOT_LOGGED_IN(1004)。
     */
    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public void checkLogin() {
        StpUtil.checkLogin();
    }
}
