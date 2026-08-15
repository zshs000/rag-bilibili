package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.MessageSourceMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantMessageTxServiceTest {
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private MessageSourceMapper messageSourceMapper;
    @Mock
    private SessionMapper sessionMapper;

    @Test
    void shouldSaveAssistantMessageAndSourcesTogether() {
        AssistantMessageTxService service = new AssistantMessageTxService(
                messageMapper, messageSourceMapper, sessionMapper);
        Session session = new Session();
        session.setId(9L);
        session.setUserId(7L);
        when(sessionMapper.selectById(9L)).thenReturn(session);
        doAnswer(invocation -> {
            invocation.<Message>getArgument(0).setId(88L);
            return 1;
        }).when(messageMapper).insert(org.mockito.ArgumentMatchers.any(Message.class));
        MessageSource source = new MessageSource();
        source.setCitationIndex(1);

        Message message = service.save(9L, 7L, "回答 [1]", List.of(source));

        assertEquals(88L, message.getId());
        assertEquals(88L, source.getMessageId());
        assertNotNull(source.getCreateTime());
        verify(messageSourceMapper).batchInsert(List.of(source));
    }

    @Test
    void shouldRejectAssistantWriteWhenSessionIsNotOwnedByUser() {
        AssistantMessageTxService service = new AssistantMessageTxService(
                messageMapper, messageSourceMapper, sessionMapper);
        Session session = new Session();
        session.setId(9L);
        session.setUserId(8L);
        when(sessionMapper.selectById(9L)).thenReturn(session);

        assertThrows(BusinessException.class,
                () -> service.save(9L, 7L, "回答", List.of()));

        verify(messageMapper, never()).insert(org.mockito.ArgumentMatchers.any(Message.class));
    }
}
