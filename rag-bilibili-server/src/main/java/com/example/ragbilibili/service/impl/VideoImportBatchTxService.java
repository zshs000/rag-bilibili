package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.entity.VideoImportItem;
import com.example.ragbilibili.enums.VideoImportBatchStatus;
import com.example.ragbilibili.enums.VideoImportItemStatus;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.mapper.VideoImportBatchMapper;
import com.example.ragbilibili.mapper.VideoImportItemMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import com.example.ragbilibili.util.BVIDParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class VideoImportBatchTxService {
    @Autowired private VideoImportBatchMapper batchMapper;
    @Autowired private VideoImportItemMapper itemMapper;
    @Autowired private VideoMapper videoMapper;

    @Transactional
    public long createBatch(Long userId, List<String> inputs, String credentialsCiphertext) {
        LocalDateTime now = LocalDateTime.now();
        VideoImportBatch batch = new VideoImportBatch();
        batch.setUserId(userId);
        batch.setStatus(VideoImportBatchStatus.RUNNING.name());
        batch.setTotalCount(inputs.size());
        batch.setQueuedCount(0);
        batch.setRunningCount(0);
        batch.setSucceededCount(0);
        batch.setSkippedCount(0);
        batch.setFailedCount(0);
        batch.setCredentialsCiphertext(credentialsCiphertext);
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        batchMapper.insert(batch);

        Set<String> seenBvids = new HashSet<>();
        for (String input : inputs) {
            VideoImportItem item = baseItem(batch.getId(), userId, input, now);
            try {
                String bvid = BVIDParser.parse(input);
                item.setBvid(bvid);
                if (!seenBvids.add(bvid)) {
                    skip(item, "批次内重复");
                } else if (videoMapper.selectByUserIdAndBvid(userId, bvid) != null) {
                    skip(item, "视频已导入");
                } else if (itemMapper.selectActiveByUserIdAndBvid(userId, bvid) != null) {
                    skip(item, "视频正在导入");
                } else {
                    item.setStatus(VideoImportItemStatus.QUEUED.name());
                }
            } catch (BusinessException e) {
                item.setStatus(VideoImportItemStatus.FAILED.name());
                item.setFailReason("无法解析 BV 号");
                item.setFinishTime(now);
            }
            itemMapper.insert(item);
        }

        batchMapper.refreshSummary(batch.getId());
        return batch.getId();
    }

    @Transactional
    public int retryFailed(Long batchId) {
        int updated = itemMapper.resetFailedByBatchId(batchId);
        if (updated > 0) {
            batchMapper.refreshSummary(batchId);
        }
        return updated;
    }

    @Transactional
    public VideoImportItem claimNext() {
        for (int attempt = 0; attempt < 10; attempt++) {
            VideoImportItem candidate = itemMapper.selectNextQueued();
            if (candidate == null) {
                return null;
            }
            if (itemMapper.claim(candidate.getId(), LocalDateTime.now()) == 1) {
                batchMapper.refreshSummary(candidate.getBatchId());
                return itemMapper.selectById(candidate.getId());
            }
        }
        return null;
    }

    public VideoImportBatch loadBatch(Long batchId) {
        return batchMapper.selectById(batchId);
    }

    @Transactional
    public void markSucceeded(Long itemId, Long videoId, Long batchId) {
        itemMapper.markSucceeded(itemId, videoId, LocalDateTime.now());
        refreshAndClearCompleted(batchId);
    }

    @Transactional
    public void markFailed(Long itemId, String failReason, Long batchId) {
        itemMapper.markFailed(itemId, failReason, LocalDateTime.now());
        refreshAndClearCompleted(batchId);
    }

    @Transactional
    public void recoverInterruptedItems() {
        if (itemMapper.resetRunningToQueued() > 0) {
            batchMapper.refreshRunningSummaries();
        }
    }

    public boolean hasQueuedItems() {
        return itemMapper.selectNextQueued() != null;
    }

    private void refreshAndClearCompleted(Long batchId) {
        batchMapper.refreshSummary(batchId);
        VideoImportBatch batch = batchMapper.selectById(batchId);
        if (batch != null && VideoImportBatchStatus.COMPLETED.name().equals(batch.getStatus())) {
            batchMapper.clearCredentials(batchId);
        }
    }

    private VideoImportItem baseItem(Long batchId, Long userId, String input, LocalDateTime now) {
        VideoImportItem item = new VideoImportItem();
        item.setBatchId(batchId);
        item.setUserId(userId);
        item.setOriginalInput(input);
        item.setRetryCount(0);
        item.setCreateTime(now);
        return item;
    }

    private void skip(VideoImportItem item, String reason) {
        item.setStatus(VideoImportItemStatus.SKIPPED.name());
        item.setFailReason(reason);
        item.setFinishTime(LocalDateTime.now());
    }
}
