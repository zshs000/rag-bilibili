package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.response.MessageResponse;
import java.util.List;

/**
 * 消息服务接口
 */
public interface MessageService {
    /**
     * 获取会话消息列表
     */
    List<MessageResponse> listMessages(Long sessionId, Long userId);
}
