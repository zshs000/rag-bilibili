package com.example.ragbilibili.config;

import com.example.ragbilibili.dto.sse.SseContentEvent;
import com.example.ragbilibili.dto.sse.SseEndEvent;
import com.example.ragbilibili.dto.sse.SseErrorEvent;
import com.example.ragbilibili.dto.sse.SseStartEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeStartEventWithStableTypeField() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new SseStartEvent(11L)));

        assertEquals("start", json.get("type").asText());
        assertEquals(11L, json.get("userMessageId").asLong());
    }

    @Test
    void shouldSerializeContentEventWithDelta() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new SseContentEvent("增量内容")));

        assertEquals("content", json.get("type").asText());
        assertEquals("增量内容", json.get("delta").asText());
    }

    @Test
    void shouldSerializeEndEventWithAssistantMessageIdAndFullContent() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new SseEndEvent(22L, "完整回答")));

        assertEquals("end", json.get("type").asText());
        assertEquals(22L, json.get("assistantMessageId").asLong());
        assertEquals("完整回答", json.get("fullContent").asText());
    }

    @Test
    void shouldSerializeErrorEventWithMessage() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new SseErrorEvent("请求失败")));

        assertEquals("error", json.get("type").asText());
        assertEquals("请求失败", json.get("message").asText());
    }
}
