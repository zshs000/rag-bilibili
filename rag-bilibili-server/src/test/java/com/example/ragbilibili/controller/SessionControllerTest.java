package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.dto.request.CreateSessionRequest;
import com.example.ragbilibili.dto.response.SessionResponse;
import com.example.ragbilibili.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private AuthSessionManager authSessionManager;

    @BeforeEach
    void mockAuth() {
        when(authSessionManager.currentUserId()).thenReturn(1L);
    }

    @Test
    void testCreateSingleVideoSession() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setSessionType("SINGLE_VIDEO");
        request.setVideoId(1L);

        SessionResponse response = new SessionResponse();
        response.setId(1L);
        response.setSessionType("SINGLE_VIDEO");
        response.setVideoId(1L);
        response.setVideoTitle("测试视频");

        when(sessionService.createSession(any(CreateSessionRequest.class), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer mocked.satoken.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionType").value("SINGLE_VIDEO"))
                .andExpect(jsonPath("$.data.videoId").value(1))
                .andExpect(jsonPath("$.data.videoTitle").value("测试视频"));
    }

    @Test
    void testCreateAllVideosSession() throws Exception {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setSessionType("ALL_VIDEOS");

        SessionResponse response = new SessionResponse();
        response.setId(2L);
        response.setSessionType("ALL_VIDEOS");

        when(sessionService.createSession(any(CreateSessionRequest.class), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer mocked.satoken.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionType").value("ALL_VIDEOS"))
                .andExpect(jsonPath("$.data.videoId").doesNotExist());
    }

    @Test
    void testListSessions() throws Exception {
        SessionResponse session1 = new SessionResponse();
        session1.setId(1L);
        session1.setSessionType("SINGLE_VIDEO");
        session1.setVideoId(1L);
        session1.setVideoTitle("视频1");

        SessionResponse session2 = new SessionResponse();
        session2.setId(2L);
        session2.setSessionType("ALL_VIDEOS");

        List<SessionResponse> sessions = Arrays.asList(session1, session2);
        when(sessionService.listSessions(1L)).thenReturn(sessions);

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sessionType").value("SINGLE_VIDEO"))
                .andExpect(jsonPath("$.data[1].sessionType").value("ALL_VIDEOS"));
    }

    @Test
    void testGetSession() throws Exception {
        SessionResponse response = new SessionResponse();
        response.setId(1L);
        response.setSessionType("SINGLE_VIDEO");
        response.setVideoId(1L);
        response.setVideoTitle("测试视频");

        when(sessionService.getSession(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/sessions/1")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.sessionType").value("SINGLE_VIDEO"));
    }

    @Test
    void testDeleteSession() throws Exception {
        doNothing().when(sessionService).deleteSession(1L, 1L);

        mockMvc.perform(delete("/api/sessions/1")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
