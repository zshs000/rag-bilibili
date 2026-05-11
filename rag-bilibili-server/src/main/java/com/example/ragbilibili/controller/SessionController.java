package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.CreateSessionRequest;
import com.example.ragbilibili.dto.response.SessionResponse;
import com.example.ragbilibili.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    private AuthSessionManager authSessionManager;

    @PostMapping
    public Result<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return Result.success(sessionService.createSession(request, authSessionManager.currentUserId()));
    }

    @GetMapping
    public Result<List<SessionResponse>> listSessions() {
        return Result.success(sessionService.listSessions(authSessionManager.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<SessionResponse> getSession(@PathVariable Long id) {
        return Result.success(sessionService.getSession(id, authSessionManager.currentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id, authSessionManager.currentUserId());
        return Result.success();
    }
}
