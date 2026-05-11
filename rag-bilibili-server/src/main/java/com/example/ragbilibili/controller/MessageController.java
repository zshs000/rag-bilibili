package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.SendMessageRequest;
import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.service.ChatService;
import com.example.ragbilibili.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 消息控制器
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
public class MessageController {
    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AuthSessionManager authSessionManager;

    @PostMapping("/stream")
    public SseEmitter streamMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.streamMessage(sessionId, request.getContent(), authSessionManager.currentUserId());
    }

    @GetMapping
    public Result<List<MessageResponse>> listMessages(@PathVariable Long sessionId) {
        return Result.success(messageService.listMessages(sessionId, authSessionManager.currentUserId()));
    }
}
