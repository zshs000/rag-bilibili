package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Chunk;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoImportTxServiceTest {
    @Mock private VideoMapper videoMapper;
    @Mock private ChunkMapper chunkMapper;
    @Mock private VectorMappingMapper vectorMappingMapper;
    @InjectMocks private VideoImportTxService service;

    @Test
    void finalizeImportSuccessShouldPersistChunkTiming() {
        Video video = new Video();
        video.setId(100L);
        PreparedVideoImportData.PreparedChunkPayload payload =
                new PreparedVideoImportData.PreparedChunkPayload(
                        0, 1, "字幕正文", "vector-1", 123L, 2, 1250L, 3500L, "ai-zh");
        PreparedVideoImportData prepared = new PreparedVideoImportData(
                "BV1Test", "标题", "简介", List.of(Document.builder().text("字幕正文").build()),
                List.of("vector-1"), List.of(payload));

        service.finalizeImportSuccess(video, 1L, prepared);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Chunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkMapper).batchInsert(chunksCaptor.capture());
        Chunk chunk = chunksCaptor.getValue().get(0);
        assertEquals(123L, chunk.getCid());
        assertEquals(2, chunk.getPageNumber());
        assertEquals(1250L, chunk.getStartTimeMs());
        assertEquals(3500L, chunk.getEndTimeMs());
        assertEquals("ai-zh", chunk.getSubtitleLanguage());
    }

    @Test
    void createImportingVideoShouldReuseFailedRecordForRetry() {
        Video failed = new Video();
        failed.setId(100L);
        failed.setStatus("FAILED");
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1Test"))
                .thenReturn(failed);
        PreparedVideoImportData prepared = new PreparedVideoImportData(
                "BV1Test", "新标题", "新简介", List.of(), List.of(), List.of());

        Video result = service.createImportingVideo(prepared, 1L);

        assertSame(failed, result);
        assertEquals("IMPORTING", result.getStatus());
        assertEquals("新标题", result.getTitle());
        verify(videoMapper).update(any(Video.class));
    }
}
