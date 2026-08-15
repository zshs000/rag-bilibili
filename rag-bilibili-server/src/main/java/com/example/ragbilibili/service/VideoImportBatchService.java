package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.request.CreateVideoImportBatchRequest;
import com.example.ragbilibili.dto.response.VideoImportBatchResponse;

import java.util.List;

public interface VideoImportBatchService {
    VideoImportBatchResponse createBatch(CreateVideoImportBatchRequest request, Long userId);

    List<VideoImportBatchResponse> listBatches(Long userId);

    VideoImportBatchResponse getBatch(Long batchId, Long userId);

    VideoImportBatchResponse retryFailed(Long batchId, Long userId);
}
