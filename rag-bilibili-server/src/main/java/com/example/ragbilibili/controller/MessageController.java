package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.SendMessageRequest;
import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.service.ChatService;
import com.example.ragbilibili.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpSession;
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

    @PostMapping("/stream")
    public SseEmitter streamMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return chatService.streamMessage(sessionId, request.getContent(), userId);
    }

    @GetMapping
    public Result<List<MessageResponse>> listMessages(
            @PathVariable Long sessionId,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        List<MessageResponse> messages = messageService.listMessages(sessionId, userId);
        return Result.success(messages);
    }
}
