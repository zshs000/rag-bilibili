package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.dto.response.VideoImportBatchResponse;
import com.example.ragbilibili.service.VideoImportBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoImportBatchController.class)
class VideoImportBatchControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private VideoImportBatchService service;
    @MockBean private AuthSessionManager authSessionManager;

    @BeforeEach
    void setUp() {
        when(authSessionManager.currentUserId()).thenReturn(7L);
    }

    @Test
    void exposesCreateListDetailAndRetryEndpoints() throws Exception {
        VideoImportBatchResponse response = new VideoImportBatchResponse();
        response.setId(10L);
        response.setStatus("RUNNING");
        when(service.createBatch(any(), org.mockito.ArgumentMatchers.eq(7L))).thenReturn(response);
        when(service.listBatches(7L)).thenReturn(List.of(response));
        when(service.getBatch(10L, 7L)).thenReturn(response);
        when(service.retryFailed(10L, 7L)).thenReturn(response);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "inputs", List.of("BV1xx411c7mD"),
                "sessdata", "sess", "biliJct", "csrf", "buvid3", "buvid"));

        mockMvc.perform(post("/api/video-import-batches")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(10));
        mockMvc.perform(get("/api/video-import-batches").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/video-import-batches/10").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));
        mockMvc.perform(post("/api/video-import-batches/10/retry-failed").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(10));
    }
}
