package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
import com.example.ragbilibili.config.SubtitleProbeProperties;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.entity.Chunk;
import com.example.ragbilibili.entity.VectorMapping;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.VideoStatus;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import com.example.ragbilibili.probe.PlaywrightSubtitleProbeService;
import com.example.ragbilibili.probe.SubtitleProbeResult;
import com.example.ragbilibili.transformer.SubtitleCleaningTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceImplTest {

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

    @Mock
    private TokenTextSplitter tokenTextSplitter;

    @Mock
    private SubtitleCleaningTransformer subtitleCleaningTransformer;

    @Mock
    private DashVectorStore dashVectorStore;

    @Mock
    private VideoStatusWriter videoStatusWriter;

    @Mock
    private PlaywrightSubtitleProbeService subtitleProbeService;

    @Mock
    private SubtitleProbeProperties subtitleProbeProperties;

    @InjectMocks
    private VideoServiceImpl videoService;

    @Test
    void importVideoShouldInsertVideoAfterFetchingTitle() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        stubInsertAndVectorFlow();

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            VideoResponse response = videoService.importVideo(request, 1L);

            ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
            verify(videoMapper).insert(videoCaptor.capture());
            Video insertedVideo = videoCaptor.getValue();

            assertEquals("BV1KMwgeKECx", insertedVideo.getBvid());
            assertEquals("测试标题", insertedVideo.getTitle());
            assertEquals("测试描述", insertedVideo.getDescription());
            assertEquals(VideoStatus.SUCCESS.getCode(), response.getStatus());
            assertEquals("测试标题", response.getTitle());
            assertNotNull(response.getId());
            verify(dashVectorStore, times(1)).add(any());
            verify(subtitleProbeService, never()).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldRejectWhenCleaningRemovesAllSubtitleSegments() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = sourceDocument();
        Document cleanedDocument = Document.builder()
                .text("Video Title: 测试标题\nTranscript:")
                .metadata(new HashMap<>())
                .metadata("subtitle_segment_count", 0)
                .build();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(cleanedDocument));

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> videoService.importVideo(request, 1L)
            );

            assertEquals(ErrorCode.VIDEO_NO_SUBTITLE.getCode(), exception.getCode());
            assertEquals("已读取到字幕，但清洗后未保留有效内容，当前视频暂不支持导入。", exception.getMessage());
            verify(videoMapper, never()).insert(any(Video.class));
            verify(tokenTextSplitter, never()).apply(any());
            verify(dashVectorStore, never()).add(any());
            verify(videoStatusWriter, never()).markFailed(any(Video.class), any());
            verify(subtitleProbeService, never()).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldTellUserToCheckSubtitleButtonWhenProbeFindsNoButton() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);
        when(subtitleProbeService.probe(any(), any())).thenReturn(SubtitleProbeResult.noSubtitleButton("no button"));

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of()))) {

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> videoService.importVideo(request, 1L)
            );

            assertEquals(ErrorCode.VIDEO_NO_SUBTITLE.getCode(), exception.getCode());
            assertEquals(
                    "未检测到 B 站官方字幕（含 AI 字幕）。请先去视频主页确认播放器右下角是否有“字幕”按钮；若没有，则当前视频暂不支持导入。",
                    exception.getMessage()
            );
            verify(videoMapper, never()).insert(any(Video.class));
            verify(subtitleCleaningTransformer, never()).apply(any());
            verify(tokenTextSplitter, never()).apply(any());
            verify(dashVectorStore, never()).add(any());
            verify(subtitleProbeService, times(1)).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldRetryWhenProbeFindsSubtitleButton() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);
        when(subtitleProbeService.probe(any(), any())).thenReturn(SubtitleProbeResult.hasSubtitleButton("button found"));
        when(subtitleProbeProperties.getRetryDelaysMillis()).thenReturn(new long[]{0, 0, 0});

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();
        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        stubInsertAndVectorFlow();

        AtomicInteger attemptCounter = new AtomicInteger();
        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> {
                    int attempt = attemptCounter.incrementAndGet();
                    if (attempt < 3) {
                        when(mock.get()).thenReturn(List.of());
                    } else {
                        when(mock.get()).thenReturn(List.of(sourceDocument));
                    }
                })) {

            VideoResponse response = videoService.importVideo(request, 1L);

            assertEquals(VideoStatus.SUCCESS.getCode(), response.getStatus());
            verify(subtitleProbeService, times(1)).probe(any(), any());
            verify(videoMapper, times(1)).insert(any(Video.class));
            verify(dashVectorStore, times(1)).add(any());
        }
    }

    @Test
    void importVideoShouldFailAfterRetriesWhenProbeFindsSubtitleButton() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);
        when(subtitleProbeService.probe(any(), any())).thenReturn(SubtitleProbeResult.hasSubtitleButton("button found"));
        when(subtitleProbeProperties.getRetryDelaysMillis()).thenReturn(new long[]{0, 0, 0});

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of()))) {

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> videoService.importVideo(request, 1L)
            );

            assertEquals(ErrorCode.VIDEO_NO_SUBTITLE.getCode(), exception.getCode());
            assertEquals(
                    "已检测到视频主页存在“字幕”按钮，但当前官方字幕接口暂未返回内容，可能仍在处理或发生了短暂波动，请稍后重试。",
                    exception.getMessage()
            );
            verify(subtitleProbeService, times(1)).probe(any(), any());
            verify(videoMapper, never()).insert(any(Video.class));
        }
    }

    /**
     * 暴露 Bug：@Transactional + catch-rethrow 导致 FAILED 状态丢失。
     */
    @Test
    void importVideoFailure_failedStatusUpdateIsCalledButWouldBeRolledBackByTransaction() {
        ImportVideoRequest request = buildRequest();
        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));

        doAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setId(100L);
            return 1;
        }).when(videoMapper).insert(any(Video.class));

        doAnswer(invocation -> {
            List<Chunk> chunks = invocation.getArgument(0);
            long id = 200L;
            for (Chunk chunk : chunks) {
                chunk.setId(id++);
            }
            return chunks.size();
        }).when(chunkMapper).batchInsert(any());

        doThrow(new RuntimeException("DashVector 写入失败")).when(dashVectorStore).add(any());

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            assertThrows(BusinessException.class, () -> videoService.importVideo(request, 1L));

            ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
            verify(videoStatusWriter, times(1)).markFailed(
                    videoCaptor.capture(),
                    eq("DashVector 写入失败")
            );
            assertEquals(100L, videoCaptor.getValue().getId());
        }
    }

    private ImportVideoRequest buildRequest() {
        ImportVideoRequest request = new ImportVideoRequest();
        request.setBvidOrUrl("BV1KMwgeKECx");
        request.setSessdata("sessdata");
        request.setBiliJct("biliJct");
        request.setBuvid3("buvid3");
        return request;
    }

    private Document sourceDocument() {
        return Document.builder()
                .text("视频原文")
                .metadata(new HashMap<>())
                .metadata("title", "测试标题")
                .metadata("description", "测试描述")
                .build();
    }

    private Document splitDocument() {
        return Document.builder()
                .text("切分片段")
                .metadata(new HashMap<>())
                .build();
    }

    private void stubInsertAndVectorFlow() {
        doAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setId(100L);
            return 1;
        }).when(videoMapper).insert(any(Video.class));

        doAnswer(invocation -> {
            List<Chunk> chunks = invocation.getArgument(0);
            long id = 200L;
            for (Chunk chunk : chunks) {
                chunk.setId(id++);
            }
            return chunks.size();
        }).when(chunkMapper).batchInsert(any());

        when(vectorMappingMapper.batchInsert(any())).thenAnswer(invocation -> {
            List<VectorMapping> mappings = invocation.getArgument(0);
            return mappings.size();
        });
        when(videoMapper.update(any(Video.class))).thenReturn(1);
        when(chunkMapper.countByVideoId(100L)).thenReturn(1);
        doNothing().when(dashVectorStore).add(any());
    }
}
