package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.service.RagDependencyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceAvailabilityTest {
    @Mock private SessionMapper sessionMapper;
    @Mock private MessageMapper messageMapper;
    @Mock private RagDependencyProvider ragDependencyProvider;
    @InjectMocks private ChatServiceImpl service;

    @Test
    void rejectsBeforePersistingUserMessageWhenRagIsUnavailable() {
        Session session = new Session();
        session.setId(10L);
        session.setUserId(7L);
        when(sessionMapper.selectById(10L)).thenReturn(session);
        when(ragDependencyProvider.requireVectorStore())
                .thenThrow(new BusinessException(ErrorCode.RAG_UNAVAILABLE));

        assertThatThrownBy(() -> service.streamMessage(10L, "question", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4003);
        verify(messageMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
