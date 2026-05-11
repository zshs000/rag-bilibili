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

    public Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public void checkLogin() {
        StpUtil.checkLogin();
    }
}
