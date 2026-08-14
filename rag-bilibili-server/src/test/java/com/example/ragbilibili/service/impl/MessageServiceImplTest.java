package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.response.MessageSourceResponse;
import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.MessageSourceMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.service.CitationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private MessageSourceMapper messageSourceMapper;
    @Mock
    private CitationService citationService;
    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void shouldReturnPersistedSourcesWhenReloadingHistory() {
        Session session = new Session();
        session.setId(5L);
        session.setUserId(7L);
        Message message = new Message();
        message.setId(11L);
        message.setSessionId(5L);
        message.setRole("ASSISTANT");
        message.setContent("回答[1]");
        message.setCreateTime(LocalDateTime.of(2026, 8, 15, 1, 0));
        MessageSource source = new MessageSource();
        source.setMessageId(11L);
        source.setCitationIndex(1);
        MessageSourceResponse response = new MessageSourceResponse();
        response.setIndex(1);

        when(sessionMapper.selectById(5L)).thenReturn(session);
        when(messageMapper.selectBySessionId(5L)).thenReturn(List.of(message));
        when(messageSourceMapper.selectByMessageIds(List.of(11L))).thenReturn(List.of(source));
        when(citationService.toResponses(List.of(source))).thenReturn(List.of(response));

        var messages = messageService.listMessages(5L, 7L);

        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).getSources().get(0).getIndex());
    }
}
