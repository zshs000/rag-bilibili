package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.VideoImportBatch;
import com.example.ragbilibili.entity.VideoImportItem;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.mapper.VideoImportBatchMapper;
import com.example.ragbilibili.mapper.VideoImportItemMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoImportBatchTxServiceTest {
    @Mock private VideoImportBatchMapper batchMapper;
    @Mock private VideoImportItemMapper itemMapper;
    @Mock private VideoMapper videoMapper;
    @InjectMocks private VideoImportBatchTxService service;

    @Test
    void createsQueuedSkippedAndFailedItemsWithoutFailingWholeBatch() {
        doAnswer(invocation -> {
            VideoImportBatch batch = invocation.getArgument(0);
            batch.setId(10L);
            return 1;
        }).when(batchMapper).insert(any(VideoImportBatch.class));

        long batchId = service.createBatch(
                7L,
                List.of("BV1xx411c7mD", "https://bilibili.com/video/BV1xx411c7mD", "不是视频"),
                "ciphertext");

        assertThat(batchId).isEqualTo(10L);
        ArgumentCaptor<VideoImportItem> itemCaptor = ArgumentCaptor.forClass(VideoImportItem.class);
        verify(itemMapper, org.mockito.Mockito.times(3)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(VideoImportItem::getStatus)
                .containsExactly("QUEUED", "SKIPPED", "FAILED");
        assertThat(itemCaptor.getAllValues().get(2).getFailReason()).isEqualTo("无法解析 BV 号");
        verify(batchMapper).refreshSummary(10L);
    }

    @Test
    void clearsCredentialsWhenEveryItemIsSkipped() {
        doAnswer(invocation -> {
            VideoImportBatch batch = invocation.getArgument(0);
            batch.setId(10L);
            return 1;
        }).when(batchMapper).insert(any(VideoImportBatch.class));
        when(videoMapper.selectByUserIdAndBvid(7L, "BV1xx411c7mD"))
                .thenReturn(new Video());

        service.createBatch(7L, List.of("BV1xx411c7mD"), "ciphertext");

        verify(batchMapper).clearCredentials(10L);
    }
}
