package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.enums.MessageRole;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.MessageSourceMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssistantMessageTxService {
    private final MessageMapper messageMapper;
    private final MessageSourceMapper messageSourceMapper;
    private final SessionMapper sessionMapper;

    public AssistantMessageTxService(MessageMapper messageMapper,
                                     MessageSourceMapper messageSourceMapper,
                                     SessionMapper sessionMapper) {
        this.messageMapper = messageMapper;
        this.messageSourceMapper = messageSourceMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional
    public Message save(Long sessionId, Long userId, String content, List<MessageSource> sources) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        Message assistantMessage = new Message();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
        assistantMessage.setContent(content);
        assistantMessage.setCreateTime(now);
        messageMapper.insert(assistantMessage);

        if (sources != null && !sources.isEmpty()) {
            sources.forEach(source -> {
                source.setMessageId(assistantMessage.getId());
                source.setCreateTime(now);
            });
            messageSourceMapper.batchInsert(sources);
        }
        return assistantMessage;
    }
}
