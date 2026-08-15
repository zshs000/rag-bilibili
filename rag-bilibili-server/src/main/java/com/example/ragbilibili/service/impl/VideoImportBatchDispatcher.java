package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.entity.VideoImportItem;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.service.BatchCredentialCipher;
import com.example.ragbilibili.service.BatchImportCredentials;
import com.example.ragbilibili.service.VideoImportBatchScheduler;
import com.example.ragbilibili.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VideoImportBatchDispatcher implements VideoImportBatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(VideoImportBatchDispatcher.class);
    private static final int WORKER_COUNT = 2;
    private static final int MAX_REASON_LENGTH = 500;

    private final VideoImportBatchTxService txService;
    private final VideoService videoService;
    private final BatchCredentialCipher credentialCipher;
    private final TaskExecutor batchImportExecutor;
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public VideoImportBatchDispatcher(VideoImportBatchTxService txService,
                                      VideoService videoService,
                                      BatchCredentialCipher credentialCipher,
                                      @Qualifier("batchImportExecutor") TaskExecutor batchImportExecutor) {
        this.txService = txService;
        this.videoService = videoService;
        this.credentialCipher = credentialCipher;
        this.batchImportExecutor = batchImportExecutor;
    }

    @Override
    public void dispatch() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        AtomicInteger remainingWorkers = new AtomicInteger(WORKER_COUNT);
        int submitted = 0;
        try {
            for (int i = 0; i < WORKER_COUNT; i++) {
                batchImportExecutor.execute(() -> drain(remainingWorkers));
                submitted++;
            }
        } catch (RejectedExecutionException e) {
            log.warn("批量导入执行器暂时繁忙");
            for (int i = submitted; i < WORKER_COUNT; i++) {
                workerFinished(remainingWorkers);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAndDispatch() {
        txService.recoverInterruptedItems();
        dispatch();
    }

    @Scheduled(fixedDelay = 5000)
    public void compensateMissedNotification() {
        if (txService.hasQueuedItems()) {
            dispatch();
        }
    }

    private void drain(AtomicInteger remainingWorkers) {
        try {
            VideoImportItem item;
            while ((item = txService.claimNext()) != null) {
                process(item);
            }
        } finally {
            workerFinished(remainingWorkers);
        }
    }

    private void workerFinished(AtomicInteger remainingWorkers) {
        if (remainingWorkers.decrementAndGet() == 0) {
            draining.set(false);
            if (txService.hasQueuedItems()) {
                dispatch();
            }
        }
    }

    private void process(VideoImportItem item) {
        try {
            VideoImportBatch batch = txService.loadBatch(item.getBatchId());
            if (batch == null || batch.getCredentialsCiphertext() == null) {
                throw new IllegalStateException("batch credentials unavailable");
            }
            BatchImportCredentials credentials = credentialCipher.decrypt(batch.getCredentialsCiphertext());
            ImportVideoRequest request = new ImportVideoRequest();
            request.setBvidOrUrl(item.getBvid());
            request.setSessdata(credentials.sessdata());
            request.setBiliJct(credentials.biliJct());
            request.setBuvid3(credentials.buvid3());
            VideoResponse response = videoService.importVideo(request, item.getUserId());
            txService.markSucceeded(item.getId(), response == null ? null : response.getId(), item.getBatchId());
        } catch (BusinessException e) {
            txService.markFailed(item.getId(), truncate(e.getMessage()), item.getBatchId());
        } catch (Exception e) {
            log.error("批量导入明细执行失败: batchId={}, itemId={}, exceptionType={}",
                    item.getBatchId(), item.getId(), e.getClass().getSimpleName());
            txService.markFailed(item.getId(), "导入失败，请稍后重试", item.getBatchId());
        }
    }

    private String truncate(String reason) {
        String safe = reason == null || reason.isBlank() ? "视频导入失败" : reason;
        return safe.length() <= MAX_REASON_LENGTH ? safe : safe.substring(0, MAX_REASON_LENGTH);
    }
}
