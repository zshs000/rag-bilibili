package com.example.ragbilibili.controller;

import com.example.ragbilibili.dto.request.SendMessageRequest;
import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.service.ChatService;
import com.example.ragbilibili.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private MessageService messageService;

    @Test
    void testStreamMessage() throws Exception {
        // 准备测试数据
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("什么是 Spring Boot？");

        // Mock Service 行为 - 返回一个 SseEmitter
        SseEmitter emitter = new SseEmitter();
        when(chatService.streamMessage(eq(1L), eq("什么是 Spring Boot？"), eq(1L))).thenReturn(emitter);

        // 执行测试
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        mockMvc.perform(post("/api/sessions/1/messages/stream")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 注意：SSE 流式响应的完整测试比较复杂，这里只验证接口能正常调用
    }

    @Test
    void testListMessages() throws Exception {
        // 准备测试数据
        MessageResponse message1 = new MessageResponse();
        message1.setId(1L);
        message1.setRole("USER");
        message1.setContent("什么是 Spring Boot？");

        MessageResponse message2 = new MessageResponse();
        message2.setId(2L);
        message2.setRole("ASSISTANT");
        message2.setContent("Spring Boot 是一个基于 Spring 框架的快速开发框架...");

        List<MessageResponse> messages = Arrays.asList(message1, message2);

        // Mock Service 行为
        when(messageService.listMessages(1L, 1L)).thenReturn(messages);

        // 执行测试
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 1L);

        mockMvc.perform(get("/api/sessions/1/messages")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("什么是 Spring Boot？"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].content").value("Spring Boot 是一个基于 Spring 框架的快速开发框架..."));
    }
}
