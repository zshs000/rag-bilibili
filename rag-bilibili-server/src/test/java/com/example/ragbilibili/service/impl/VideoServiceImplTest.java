package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
import com.example.ragbilibili.config.SubtitleProbeProperties;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private VideoImportTxService videoImportTxService;

    @Mock
    private PlaywrightSubtitleProbeService subtitleProbeService;

    @Mock
    private SubtitleProbeProperties subtitleProbeProperties;

    @InjectMocks
    private VideoServiceImpl videoService;

    @Test
    void importVideoShouldInsertVideoAfterFetchingTitle() {
        ImportVideoRequest request = buildRequest();

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        stubSuccessfulImportFlow();

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            VideoResponse response = videoService.importVideo(request, 1L);

            assertEquals(VideoStatus.SUCCESS.getCode(), response.getStatus());
            assertEquals("测试标题", response.getTitle());
            assertNotNull(response.getId());
            verify(dashVectorStore, times(1)).add(any());
            verify(videoImportTxService, times(1)).createImportingVideo(any(PreparedVideoImportData.class), eq(1L));
            verify(videoImportTxService, times(1)).finalizeImportSuccess(any(Video.class), eq(1L), any(PreparedVideoImportData.class));
            verify(subtitleProbeService, never()).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldRejectWhenCleaningRemovesAllSubtitleSegments() {
        ImportVideoRequest request = buildRequest();

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
            verify(videoImportTxService, never()).createImportingVideo(any(), any());
            verify(tokenTextSplitter, never()).apply(any());
            verify(dashVectorStore, never()).add(any());
            verify(videoStatusWriter, never()).markFailed(any(Video.class), any());
            verify(subtitleProbeService, never()).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldTellUserToCheckSubtitleButtonWhenProbeFindsNoButton() {
        ImportVideoRequest request = buildRequest();
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
            verify(videoImportTxService, never()).createImportingVideo(any(), any());
            verify(subtitleCleaningTransformer, never()).apply(any());
            verify(tokenTextSplitter, never()).apply(any());
            verify(dashVectorStore, never()).add(any());
            verify(subtitleProbeService, times(1)).probe(any(), any());
        }
    }

    @Test
    void importVideoShouldRetryWhenProbeFindsSubtitleButton() {
        ImportVideoRequest request = buildRequest();
        when(subtitleProbeService.probe(any(), any())).thenReturn(SubtitleProbeResult.hasSubtitleButton("button found"));
        when(subtitleProbeProperties.getRetryDelaysMillis()).thenReturn(new long[]{0, 0, 0});

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();
        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        stubSuccessfulImportFlow();

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
            verify(videoImportTxService, times(1)).createImportingVideo(any(PreparedVideoImportData.class), eq(1L));
            verify(dashVectorStore, times(1)).add(any());
        }
    }

    @Test
    void importVideoShouldFailAfterRetriesWhenProbeFindsSubtitleButton() {
        ImportVideoRequest request = buildRequest();
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
            verify(videoImportTxService, never()).createImportingVideo(any(), any());
        }
    }

    @Test
    void importVideoShouldMarkFailedWhenVectorWriteFails() {
        ImportVideoRequest request = buildRequest();

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        Video importingVideo = importingVideo();
        when(videoImportTxService.createImportingVideo(any(PreparedVideoImportData.class), eq(1L))).thenReturn(importingVideo);
        doThrow(new RuntimeException("DashVector 写入失败")).when(dashVectorStore).add(any());

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            assertThrows(BusinessException.class, () -> videoService.importVideo(request, 1L));

            ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
            verify(videoStatusWriter, times(1)).markFailed(videoCaptor.capture(), eq("DashVector 写入失败"));
            assertEquals(100L, videoCaptor.getValue().getId());
            verify(dashVectorStore, never()).delete(org.mockito.ArgumentMatchers.<java.util.List<String>>any());
            verify(videoImportTxService, never()).finalizeImportSuccess(any(Video.class), any(), any());
        }
    }

    @Test
    void importVideoShouldDeleteVectorsWhenFinalizeFailsAfterVectorWrite() {
        ImportVideoRequest request = buildRequest();

        Document sourceDocument = sourceDocument();
        Document splitDocument = splitDocument();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));
        Video importingVideo = importingVideo();
        when(videoImportTxService.createImportingVideo(any(PreparedVideoImportData.class), eq(1L))).thenReturn(importingVideo);
        doNothing().when(dashVectorStore).add(any());
        doThrow(new RuntimeException("finalize failed"))
                .when(videoImportTxService)
                .finalizeImportSuccess(any(Video.class), eq(1L), any(PreparedVideoImportData.class));

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            assertThrows(BusinessException.class, () -> videoService.importVideo(request, 1L));

            verify(dashVectorStore, times(1)).delete(org.mockito.ArgumentMatchers.<java.util.List<String>>any());
            verify(videoStatusWriter, times(1)).markFailed(any(Video.class), eq("finalize failed"));
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

    private Video importingVideo() {
        Video video = new Video();
        video.setId(100L);
        video.setUserId(1L);
        video.setBvid("BV1KMwgeKECx");
        video.setTitle("测试标题");
        video.setDescription("测试描述");
        video.setImportTime(LocalDateTime.now());
        video.setStatus(VideoStatus.IMPORTING.getCode());
        return video;
    }

    private void stubSuccessfulImportFlow() {
        when(videoImportTxService.createImportingVideo(any(PreparedVideoImportData.class), eq(1L)))
                .thenReturn(importingVideo());
        org.mockito.Mockito.doAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setStatus(VideoStatus.SUCCESS.getCode());
            return null;
        }).when(videoImportTxService)
                .finalizeImportSuccess(any(Video.class), eq(1L), any(PreparedVideoImportData.class));
        when(chunkMapper.countByVideoId(100L)).thenReturn(1);
        doNothing().when(dashVectorStore).add(any());
    }
}