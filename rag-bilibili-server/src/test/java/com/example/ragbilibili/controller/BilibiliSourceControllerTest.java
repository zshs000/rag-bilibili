package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.dto.response.BilibiliFavoriteFolderResponse;
import com.example.ragbilibili.dto.response.BilibiliVideoPageResponse;
import com.example.ragbilibili.service.BilibiliSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BilibiliSourceController.class)
class BilibiliSourceControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private BilibiliSourceService service;
    @MockBean private AuthSessionManager authSessionManager;

    @Test
    void exposesFolderFavoriteAndUpQueries() throws Exception {
        when(service.listFavoriteFolders(any())).thenReturn(List.of(
                new BilibiliFavoriteFolderResponse(10L, "默认收藏夹", 170, false)));
        when(service.listFavoriteVideos(eq(10L), any())).thenReturn(emptyPage());
        when(service.listUpVideos(any())).thenReturn(emptyPage());

        String credentials = "{\"sessdata\":\"s\",\"biliJct\":\"c\",\"buvid3\":\"b\"}";
        mockMvc.perform(post("/api/bilibili-sources/favorite-folders")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mediaCount").value(170));

        mockMvc.perform(post("/api/bilibili-sources/favorite-folders/10/videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":1,\"pageSize\":20,\"sessdata\":\"s\",\"biliJct\":\"c\",\"buvid3\":\"b\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(1));

        mockMvc.perform(post("/api/bilibili-sources/up-videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"up\":\"1045711541\",\"useCredentials\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    private BilibiliVideoPageResponse emptyPage() {
        return new BilibiliVideoPageResponse(1, 20, 0, false, List.of());
    }
}
