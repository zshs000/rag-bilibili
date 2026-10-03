package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.CreateVideoImportBatchRequest;
import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.mapper.VideoImportBatchMapper;
import com.example.ragbilibili.mapper.VideoImportItemMapper;
import com.example.ragbilibili.service.BatchCredentialCipher;
import com.example.ragbilibili.service.VideoImportBatchScheduler;
import com.example.ragbilibili.service.RagDependencyProvider;
import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoImportBatchServiceImplTest {
    @Mock private VideoImportBatchTxService txService;
    @Mock private VideoImportBatchMapper batchMapper;
    @Mock private VideoImportItemMapper itemMapper;
    @Mock private BatchCredentialCipher credentialCipher;
    @Mock private VideoImportBatchScheduler scheduler;
    @Mock private RagDependencyProvider ragDependencyProvider;
    @Mock private DashVectorStore dashVectorStore;
    @InjectMocks private VideoImportBatchServiceImpl service;

    @BeforeEach
    void setUpRagDependencies() {
        org.mockito.Mockito.lenient().when(ragDependencyProvider.requireVectorStore()).thenReturn(dashVectorStore);
    }

    @Test
    void createsBatchAndSchedulesWork() {
        CreateVideoImportBatchRequest request = request(List.of(" BV1xx411c7mD ", "", "BV1yy411c7mE"));
        when(credentialCipher.encrypt(any())).thenReturn("ciphertext");
        when(txService.createBatch(7L, List.of("BV1xx411c7mD", "BV1yy411c7mE"), "ciphertext"))
                .thenReturn(10L);
        when(batchMapper.selectByIdAndUserId(10L, 7L)).thenReturn(batch(10L, 7L));
        when(itemMapper.selectByBatchId(10L)).thenReturn(Collections.emptyList());

        assertThat(service.createBatch(request, 7L).getId()).isEqualTo(10L);

        verify(scheduler).dispatch();
    }

    @Test
    void rejectsBeforeEncryptingCredentialsWhenRagIsUnavailable() {
        when(ragDependencyProvider.requireVectorStore())
                .thenThrow(new BusinessException(com.example.ragbilibili.exception.ErrorCode.RAG_UNAVAILABLE));

        assertThatThrownBy(() -> service.createBatch(request(List.of("BV1xx411c7mD")), 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4003);
        verify(credentialCipher, never()).encrypt(any());
    }

    @Test
    void rejectsMoreThanFiftyNonBlankInputs() {
        CreateVideoImportBatchRequest request = request(
                java.util.stream.IntStream.range(0, 51).mapToObj(i -> "BV1xx411c7mD").toList());

        assertThatThrownBy(() -> service.createBatch(request, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(2007);
        verify(credentialCipher, never()).encrypt(any());
    }

    @Test
    void hidesBatchOwnedByAnotherUser() {
        when(batchMapper.selectByIdAndUserId(10L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.getBatch(10L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(2006);
    }

    @Test
    void retriesOnlyFailedItemsAndSchedulesWork() {
        when(batchMapper.selectByIdAndUserId(10L, 7L)).thenReturn(batch(10L, 7L));
        when(txService.retryFailed(10L)).thenReturn(2);
        when(itemMapper.selectByBatchId(10L)).thenReturn(Collections.emptyList());

        service.retryFailed(10L, 7L);

        verify(txService).retryFailed(10L);
        verify(scheduler).dispatch();
    }

    private CreateVideoImportBatchRequest request(List<String> inputs) {
        CreateVideoImportBatchRequest request = new CreateVideoImportBatchRequest();
        request.setInputs(inputs);
        request.setSessdata("sess");
        request.setBiliJct("csrf");
        request.setBuvid3("buvid");
        return request;
    }

    private VideoImportBatch batch(Long id, Long userId) {
        VideoImportBatch batch = new VideoImportBatch();
        batch.setId(id);
        batch.setUserId(userId);
        batch.setStatus("RUNNING");
        batch.setTotalCount(2);
        batch.setQueuedCount(2);
        batch.setRunningCount(0);
        batch.setSucceededCount(0);
        batch.setSkippedCount(0);
        batch.setFailedCount(0);
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        return batch;
    }
}
