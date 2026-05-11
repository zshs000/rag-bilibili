package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 视频控制器
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {
    @Autowired
    private VideoService videoService;

    @Autowired
    private AuthSessionManager authSessionManager;

    @PostMapping
    public Result<VideoResponse> importVideo(@Valid @RequestBody ImportVideoRequest request) {
        return Result.success(videoService.importVideo(request, authSessionManager.currentUserId()));
    }

    @GetMapping
    public Result<List<VideoResponse>> listVideos() {
        return Result.success(videoService.listVideos(authSessionManager.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<VideoResponse> getVideo(@PathVariable Long id) {
        return Result.success(videoService.getVideo(id, authSessionManager.currentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteVideo(@PathVariable Long id) {
        videoService.deleteVideo(id, authSessionManager.currentUserId());
        return Result.success();
    }
}
