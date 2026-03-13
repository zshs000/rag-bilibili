package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.LoginRequest;
import com.example.ragbilibili.dto.request.RegisterRequest;
import com.example.ragbilibili.dto.response.UserResponse;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.service.UserService;
import com.example.ragbilibili.util.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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

    @Value("${register.enabled:true}")
    private boolean registerEnabled;

    @PostMapping("/register")
    public Result<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        if (!registerEnabled) {
            throw new BusinessException(ErrorCode.REGISTER_DISABLED);
        }
        String ip = getClientIp(httpRequest);
        if (!RateLimiter.allowRegister(ip)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        String ip = getClientIp(httpRequest);
        if (!RateLimiter.allowLogin(ip)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        UserResponse response = userService.login(request);
        // 防 Session Fixation：登录成功后先销毁旧 session 再重建
        session.invalidate();
        HttpSession newSession = httpRequest.getSession(true);
        newSession.setAttribute("userId", response.getId());
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

    /**
     * 获取客户端真实 IP（兼容反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
