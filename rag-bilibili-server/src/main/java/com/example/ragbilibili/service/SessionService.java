package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.request.CreateSessionRequest;
import com.example.ragbilibili.dto.response.SessionResponse;
import java.util.List;

/**
 * 会话服务接口
 */
public interface SessionService {
    /**
     * 创建会话
     */
    SessionResponse createSession(CreateSessionRequest request, Long userId);

    /**
     * 获取会话列表
     */
    List<SessionResponse> listSessions(Long userId);

    /**
     * 获取会话详情
     */
    SessionResponse getSession(Long sessionId, Long userId);

    /**
     * 删除会话
     */
    void deleteSession(Long sessionId, Long userId);
}
