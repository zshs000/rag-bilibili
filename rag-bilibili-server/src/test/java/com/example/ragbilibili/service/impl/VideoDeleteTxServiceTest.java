package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoDeleteTxServiceTest {

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private VectorMappingMapper vectorMappingMapper;

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private VideoDeleteTxService videoDeleteTxService;

    @Test
    void deleteVideoDataShouldReturnVectorIdsAndDeleteDatabaseRows() {
        Video video = new Video();
        video.setId(100L);
        video.setUserId(1L);
        video.setBvid("BV1KMwgeKECx");
        List<String> vectorIds = List.of("1_BV1KMwgeKECx_0", "1_BV1KMwgeKECx_1");

        when(videoMapper.selectById(100L)).thenReturn(video);
        when(vectorMappingMapper.selectVectorIdsByVideoId(100L)).thenReturn(vectorIds);

        List<String> result = videoDeleteTxService.deleteVideoData(100L, 1L);

        assertEquals(vectorIds, result);
        InOrder inOrder = inOrder(videoMapper, vectorMappingMapper, sessionMapper, messageMapper, chunkMapper);
        inOrder.verify(videoMapper).selectById(100L);
        inOrder.verify(vectorMappingMapper).selectVectorIdsByVideoId(100L);
        inOrder.verify(messageMapper).deleteByVideoId(100L);
        inOrder.verify(sessionMapper).deleteByVideoId(100L);
        inOrder.verify(vectorMappingMapper).deleteByVideoId(100L);
        inOrder.verify(chunkMapper).deleteByVideoId(100L);
        inOrder.verify(videoMapper).deleteById(100L);
    }

    @Test
    void deleteVideoDataShouldDeleteMessagesByVideoIdWithoutLoadingSessionIds() {
        Video video = new Video();
        video.setId(100L);
        video.setUserId(1L);
        when(videoMapper.selectById(100L)).thenReturn(video);
        when(vectorMappingMapper.selectVectorIdsByVideoId(100L)).thenReturn(List.of());

        List<String> result = videoDeleteTxService.deleteVideoData(100L, 1L);

        assertEquals(List.of(), result);
        verify(messageMapper).deleteByVideoId(100L);
        verify(sessionMapper, never()).selectIdsByVideoId(100L);
    }

    @Test
    void deleteVideoDataShouldRejectMissingOrForeignVideo() {
        when(videoMapper.selectById(100L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> videoDeleteTxService.deleteVideoData(100L, 1L)
        );

        assertEquals(ErrorCode.VIDEO_NOT_FOUND.getCode(), exception.getCode());
        verify(vectorMappingMapper, never()).selectVectorIdsByVideoId(100L);
    }
}
