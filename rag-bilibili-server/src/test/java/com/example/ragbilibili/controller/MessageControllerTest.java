package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.dto.request.SendMessageRequest;
import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.service.ChatService;
import com.example.ragbilibili.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @MockBean
    private AuthSessionManager authSessionManager;

    @BeforeEach
    void mockAuth() {
        when(authSessionManager.currentUserId()).thenReturn(1L);
    }

    @Test
    void testStreamMessage() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("什么是 Spring Boot？");

        SseEmitter emitter = new SseEmitter();
        when(chatService.streamMessage(eq(1L), eq("什么是 Spring Boot？"), eq(1L))).thenReturn(emitter);

        mockMvc.perform(post("/api/sessions/1/messages/stream")
                        .header("Authorization", "Bearer mocked.satoken.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(chatService).streamMessage(1L, "什么是 Spring Boot？", 1L);
    }

    @Test
    void testStreamMessageShouldRejectBlankContent() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent(" ");

        mockMvc.perform(post("/api/sessions/1/messages/stream")
                        .header("Authorization", "Bearer mocked.satoken.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("消息内容不能为空"));

        verifyNoInteractions(chatService);
    }

    @Test
    void testListMessages() throws Exception {
        MessageResponse message1 = new MessageResponse();
        message1.setId(1L);
        message1.setRole("USER");
        message1.setContent("什么是 Spring Boot？");

        MessageResponse message2 = new MessageResponse();
        message2.setId(2L);
        message2.setRole("ASSISTANT");
        message2.setContent("Spring Boot 是一个基于 Spring 框架的快速开发框架...");

        List<MessageResponse> messages = Arrays.asList(message1, message2);
        when(messageService.listMessages(1L, 1L)).thenReturn(messages);

        mockMvc.perform(get("/api/sessions/1/messages")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("什么是 Spring Boot？"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].content").value("Spring Boot 是一个基于 Spring 框架的快速开发框架..."));
    }
}
