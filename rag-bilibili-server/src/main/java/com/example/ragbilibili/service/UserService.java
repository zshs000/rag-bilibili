package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.request.LoginRequest;
import com.example.ragbilibili.dto.request.RegisterRequest;
import com.example.ragbilibili.dto.response.UserResponse;

/**
 * 用户服务接口
 */
public interface UserService {
    /**
     * 用户注册
     */
    UserResponse register(RegisterRequest request);

    /**
     * 用户登录
     */
    UserResponse login(LoginRequest request);

    /**
     * 用户登出
     */
    void logout(Long userId);

    /**
     * 获取当前用户信息
     */
    UserResponse getCurrentUser(Long userId);
}
