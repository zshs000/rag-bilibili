package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.CreateSessionRequest;
import com.example.ragbilibili.dto.response.SessionResponse;
import com.example.ragbilibili.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 会话控制器
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    @Autowired
    private SessionService sessionService;

    @PostMapping
    public Result<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.success(sessionService.createSession(request, userId));
    }

    @GetMapping
    public Result<List<SessionResponse>> listSessions(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.success(sessionService.listSessions(userId));
    }

    @GetMapping("/{id}")
    public Result<SessionResponse> getSession(
            @PathVariable Long id,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.success(sessionService.getSession(id, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(
            @PathVariable Long id,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        sessionService.deleteSession(id, userId);
        return Result.success();
    }
}
