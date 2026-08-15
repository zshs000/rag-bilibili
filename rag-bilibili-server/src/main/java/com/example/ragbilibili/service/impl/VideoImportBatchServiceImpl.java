package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.CreateVideoImportBatchRequest;
import com.example.ragbilibili.dto.response.VideoImportBatchResponse;
import com.example.ragbilibili.dto.response.VideoImportItemResponse;
import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.entity.VideoImportItem;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.VideoImportBatchMapper;
import com.example.ragbilibili.mapper.VideoImportItemMapper;
import com.example.ragbilibili.service.BatchCredentialCipher;
import com.example.ragbilibili.service.BatchImportCredentials;
import com.example.ragbilibili.service.VideoImportBatchScheduler;
import com.example.ragbilibili.service.VideoImportBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class VideoImportBatchServiceImpl implements VideoImportBatchService {
    private static final int MAX_BATCH_SIZE = 50;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired private VideoImportBatchTxService txService;
    @Autowired private VideoImportBatchMapper batchMapper;
    @Autowired private VideoImportItemMapper itemMapper;
    @Autowired private BatchCredentialCipher credentialCipher;
    @Autowired private VideoImportBatchScheduler scheduler;

    @Override
    public VideoImportBatchResponse createBatch(CreateVideoImportBatchRequest request, Long userId) {
        List<String> inputs = request.getInputs().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (inputs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (inputs.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.VIDEO_IMPORT_BATCH_LIMIT_EXCEEDED);
        }

        String ciphertext = credentialCipher.encrypt(new BatchImportCredentials(
                request.getSessdata(), request.getBiliJct(), request.getBuvid3()));
        long batchId = txService.createBatch(userId, inputs, ciphertext);
        scheduler.dispatch();
        return getBatch(batchId, userId);
    }

    @Override
    public List<VideoImportBatchResponse> listBatches(Long userId) {
        return batchMapper.selectRecentByUserId(userId).stream()
                .map(batch -> toResponse(batch, List.of()))
                .toList();
    }

    @Override
    public VideoImportBatchResponse getBatch(Long batchId, Long userId) {
        VideoImportBatch batch = requireOwnedBatch(batchId, userId);
        return toResponse(batch, itemMapper.selectByBatchId(batchId));
    }

    @Override
    public VideoImportBatchResponse retryFailed(Long batchId, Long userId) {
        requireOwnedBatch(batchId, userId);
        if (txService.retryFailed(batchId) > 0) {
            scheduler.dispatch();
        }
        return getBatch(batchId, userId);
    }

    private VideoImportBatch requireOwnedBatch(Long batchId, Long userId) {
        VideoImportBatch batch = batchMapper.selectByIdAndUserId(batchId, userId);
        if (batch == null) {
            throw new BusinessException(ErrorCode.VIDEO_IMPORT_BATCH_NOT_FOUND);
        }
        return batch;
    }

    private VideoImportBatchResponse toResponse(VideoImportBatch batch, List<VideoImportItem> items) {
        VideoImportBatchResponse response = new VideoImportBatchResponse();
        response.setId(batch.getId());
        response.setStatus(batch.getStatus());
        response.setTotalCount(batch.getTotalCount());
        response.setQueuedCount(batch.getQueuedCount());
        response.setRunningCount(batch.getRunningCount());
        response.setSucceededCount(batch.getSucceededCount());
        response.setSkippedCount(batch.getSkippedCount());
        response.setFailedCount(batch.getFailedCount());
        response.setCreateTime(format(batch.getCreateTime()));
        response.setUpdateTime(format(batch.getUpdateTime()));
        response.setFinishTime(format(batch.getFinishTime()));
        response.setItems(items.stream().map(this::toItemResponse).toList());
        return response;
    }

    private VideoImportItemResponse toItemResponse(VideoImportItem item) {
        VideoImportItemResponse response = new VideoImportItemResponse();
        response.setId(item.getId());
        response.setOriginalInput(item.getOriginalInput());
        response.setBvid(item.getBvid());
        response.setStatus(item.getStatus());
        response.setFailReason(item.getFailReason());
        response.setRetryCount(item.getRetryCount());
        response.setVideoId(item.getVideoId());
        response.setCreateTime(format(item.getCreateTime()));
        response.setStartTime(format(item.getStartTime()));
        response.setFinishTime(format(item.getFinishTime()));
        return response;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(FORMATTER);
    }
}
