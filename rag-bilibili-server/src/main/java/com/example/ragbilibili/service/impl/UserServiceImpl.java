package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.LoginRequest;
import com.example.ragbilibili.dto.request.RegisterRequest;
import com.example.ragbilibili.dto.response.UserResponse;
import com.example.ragbilibili.entity.User;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.UserMapper;
import com.example.ragbilibili.service.UserService;
import com.example.ragbilibili.util.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public UserResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordEncoder.encode(request.getPassword()));
        user.setCreateTime(LocalDateTime.now());

        // 插入数据库
        userMapper.insert(user);

        return convertToResponse(user);
    }

    @Override
    public UserResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证密码
        if (!PasswordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        return convertToResponse(user);
    }

    @Override
    public void logout(Long userId) {
        // Session 认证方式，登出由 Controller 处理 session.invalidate()
        // 这里不需要额外逻辑
    }

    @Override
    public UserResponse getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToResponse(user);
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setCreateTime(user.getCreateTime().format(FORMATTER));
        return response;
    }
}
