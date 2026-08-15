package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.entity.VideoImportItem;
import com.example.ragbilibili.service.BatchCredentialCipher;
import com.example.ragbilibili.service.BatchImportCredentials;
import com.example.ragbilibili.service.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoImportBatchDispatcherTest {
    @Mock private VideoImportBatchTxService txService;
    @Mock private VideoService videoService;
    @Mock private BatchCredentialCipher credentialCipher;
    @Mock private TaskExecutor batchImportExecutor;
    @InjectMocks private VideoImportBatchDispatcher dispatcher;

    @Test
    void dispatchesExactlyTwoWorkersAndMarksSuccessfulItem() {
        VideoImportItem item = item();
        when(txService.claimNext()).thenReturn(item, null, null);
        when(txService.loadBatch(10L)).thenReturn(batch());
        when(credentialCipher.decrypt("ciphertext"))
                .thenReturn(new BatchImportCredentials("sess", "csrf", "buvid"));
        VideoResponse response = new VideoResponse();
        response.setId(99L);
        when(videoService.importVideo(any(ImportVideoRequest.class), eq(7L))).thenReturn(response);

        dispatcher.dispatch();

        ArgumentCaptor<Runnable> workers = ArgumentCaptor.forClass(Runnable.class);
        verify(batchImportExecutor, org.mockito.Mockito.times(2)).execute(workers.capture());
        workers.getAllValues().forEach(Runnable::run);
        verify(txService).markSucceeded(101L, 99L, 10L);
    }

    @Test
    void isolatesItemFailureAndContinuesDraining() {
        VideoImportItem first = item();
        VideoImportItem second = item();
        second.setId(102L);
        when(txService.claimNext()).thenReturn(first, second, null, null);
        when(txService.loadBatch(10L)).thenReturn(batch());
        when(credentialCipher.decrypt("ciphertext"))
                .thenReturn(new BatchImportCredentials("sess", "csrf", "buvid"));
        doThrow(new RuntimeException("secret detail"))
                .doReturn(null)
                .when(videoService).importVideo(any(ImportVideoRequest.class), eq(7L));

        dispatcher.dispatch();
        ArgumentCaptor<Runnable> workers = ArgumentCaptor.forClass(Runnable.class);
        verify(batchImportExecutor, org.mockito.Mockito.times(2)).execute(workers.capture());
        workers.getAllValues().forEach(Runnable::run);

        verify(txService).markFailed(101L, "导入失败，请稍后重试", 10L);
        verify(txService).markSucceeded(102L, null, 10L);
    }

    @Test
    void recoversInterruptedItemsWhenApplicationStarts() {
        dispatcher.recoverAndDispatch();

        verify(txService).recoverInterruptedItems();
        verify(batchImportExecutor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    private VideoImportItem item() {
        VideoImportItem item = new VideoImportItem();
        item.setId(101L);
        item.setBatchId(10L);
        item.setUserId(7L);
        item.setBvid("BV1xx411c7mD");
        return item;
    }

    private VideoImportBatch batch() {
        VideoImportBatch batch = new VideoImportBatch();
        batch.setId(10L);
        batch.setCredentialsCiphertext("ciphertext");
        return batch;
    }
}
