package com.example.ragbilibili.controller;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.common.Result;
import com.example.ragbilibili.dto.request.CreateVideoImportBatchRequest;
import com.example.ragbilibili.dto.response.VideoImportBatchResponse;
import com.example.ragbilibili.service.VideoImportBatchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/video-import-batches")
public class VideoImportBatchController {
    @Autowired private VideoImportBatchService service;
    @Autowired private AuthSessionManager authSessionManager;

    @PostMapping
    public Result<VideoImportBatchResponse> create(@Valid @RequestBody CreateVideoImportBatchRequest request) {
        return Result.success(service.createBatch(request, authSessionManager.currentUserId()));
    }

    @GetMapping
    public Result<List<VideoImportBatchResponse>> list() {
        return Result.success(service.listBatches(authSessionManager.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<VideoImportBatchResponse> get(@PathVariable Long id) {
        return Result.success(service.getBatch(id, authSessionManager.currentUserId()));
    }

    @PostMapping("/{id}/retry-failed")
    public Result<VideoImportBatchResponse> retryFailed(@PathVariable Long id) {
        return Result.success(service.retryFailed(id, authSessionManager.currentUserId()));
    }
}
