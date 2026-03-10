package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.LoginRequest;
import com.example.ragbilibili.dto.request.RegisterRequest;
import com.example.ragbilibili.dto.response.UserResponse;
import com.example.ragbilibili.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpSession session) {
        UserResponse response = userService.login(request);
        session.setAttribute("userId", response.getId());
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        userService.logout(userId);
        session.invalidate();
        return Result.success();
    }

    @GetMapping("/current")
    public Result<UserResponse> current(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.success(userService.getCurrentUser(userId));
    }
}
