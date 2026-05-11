package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.service.VideoService;
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

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoService videoService;

    @MockBean
    private AuthSessionManager authSessionManager;

    @BeforeEach
    void mockAuth() {
        when(authSessionManager.currentUserId()).thenReturn(1L);
    }

    @Test
    void testImportVideo() throws Exception {
        ImportVideoRequest request = new ImportVideoRequest();
        request.setBvidOrUrl("BV1xx411c7mD");
        request.setSessdata("test_sessdata");
        request.setBiliJct("test_bili_jct");
        request.setBuvid3("test_buvid3");

        VideoResponse response = new VideoResponse();
        response.setId(1L);
        response.setBvid("BV1xx411c7mD");
        response.setTitle("测试视频");
        response.setDescription("测试描述");
        response.setStatus("SUCCESS");
        response.setChunkCount(10);

        when(videoService.importVideo(any(ImportVideoRequest.class), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer mocked.satoken.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bvid").value("BV1xx411c7mD"))
                .andExpect(jsonPath("$.data.title").value("测试视频"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.chunkCount").value(10));
    }

    @Test
    void testListVideos() throws Exception {
        VideoResponse video1 = new VideoResponse();
        video1.setId(1L);
        video1.setBvid("BV1xx411c7mD");
        video1.setTitle("视频1");
        video1.setStatus("SUCCESS");

        VideoResponse video2 = new VideoResponse();
        video2.setId(2L);
        video2.setBvid("BV1yy411c7mE");
        video2.setTitle("视频2");
        video2.setStatus("SUCCESS");

        List<VideoResponse> videos = Arrays.asList(video1, video2);
        when(videoService.listVideos(1L)).thenReturn(videos);

        mockMvc.perform(get("/api/videos")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].bvid").value("BV1xx411c7mD"))
                .andExpect(jsonPath("$.data[1].bvid").value("BV1yy411c7mE"));
    }

    @Test
    void testGetVideo() throws Exception {
        VideoResponse response = new VideoResponse();
        response.setId(1L);
        response.setBvid("BV1xx411c7mD");
        response.setTitle("测试视频");
        response.setStatus("SUCCESS");

        when(videoService.getVideo(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/videos/1")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.bvid").value("BV1xx411c7mD"));
    }

    @Test
    void testDeleteVideo() throws Exception {
        doNothing().when(videoService).deleteVideo(1L, 1L);

        mockMvc.perform(delete("/api/videos/1")
                        .header("Authorization", "Bearer mocked.satoken.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
