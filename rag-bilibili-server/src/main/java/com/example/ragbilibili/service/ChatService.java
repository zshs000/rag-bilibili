package com.example.ragbilibili.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话服务接口
 */
public interface ChatService {
    /**
     * 流式发送消息
     *
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param userId 用户ID
     * @return SSE Emitter
     */
    SseEmitter streamMessage(Long sessionId, String content, Long userId);
}
