package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.MessageSourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistantMessageTxServiceTest {
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private MessageSourceMapper messageSourceMapper;

    @Test
    void shouldSaveAssistantMessageAndSourcesTogether() {
        AssistantMessageTxService service = new AssistantMessageTxService(messageMapper, messageSourceMapper);
        doAnswer(invocation -> {
            invocation.<Message>getArgument(0).setId(88L);
            return 1;
        }).when(messageMapper).insert(org.mockito.ArgumentMatchers.any(Message.class));
        MessageSource source = new MessageSource();
        source.setCitationIndex(1);

        Message message = service.save(9L, "回答 [1]", List.of(source));

        assertEquals(88L, message.getId());
        assertEquals(88L, source.getMessageId());
        assertNotNull(source.getCreateTime());
        verify(messageSourceMapper).batchInsert(List.of(source));
    }
}
