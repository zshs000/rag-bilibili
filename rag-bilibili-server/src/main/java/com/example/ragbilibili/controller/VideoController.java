package com.example.ragbilibili.controller;

import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
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

    @PostMapping
    public Result<VideoResponse> importVideo(
            @Valid @RequestBody ImportVideoRequest request,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        VideoResponse response = videoService.importVideo(request, userId);
        return Result.success(response);
    }

    @GetMapping
    public Result<List<VideoResponse>> listVideos(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        List<VideoResponse> videos = videoService.listVideos(userId);
        return Result.success(videos);
    }

    @GetMapping("/{id}")
    public Result<VideoResponse> getVideo(
            @PathVariable Long id,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        VideoResponse response = videoService.getVideo(id, userId);
        return Result.success(response);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteVideo(
            @PathVariable Long id,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        videoService.deleteVideo(id, userId);
        return Result.success();
    }
}
