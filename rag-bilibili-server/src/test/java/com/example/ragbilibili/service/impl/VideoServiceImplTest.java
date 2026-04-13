package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
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

    @InjectMocks
    private VideoServiceImpl videoService;

    @Test
    void importVideoShouldInsertVideoAfterFetchingTitle() {
        ImportVideoRequest request = new ImportVideoRequest();
        request.setBvidOrUrl("BV1KMwgeKECx");
        request.setSessdata("sessdata");
        request.setBiliJct("biliJct");
        request.setBuvid3("buvid3");

        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = Document.builder()
                .text("视频原文")
                .metadata(new HashMap<>())
                .metadata("title", "测试标题")
                .metadata("description", "测试描述")
                .build();

        Document splitDocument = Document.builder()
                .text("切分片段")
                .metadata(new HashMap<>())
                .build();

        List<Document> documents = List.of(sourceDocument);
        List<Document> splitDocuments = List.of(splitDocument);

        when(subtitleCleaningTransformer.apply(documents)).thenReturn(documents);
        when(tokenTextSplitter.apply(documents)).thenReturn(splitDocuments);

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

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(documents))) {

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
        }
    }

    @Test
    void importVideoShouldRejectWhenCleaningRemovesAllSubtitleSegments() {
        ImportVideoRequest request = new ImportVideoRequest();
        request.setBvidOrUrl("BV1KMwgeKECx");
        request.setSessdata("sessdata");
        request.setBiliJct("biliJct");
        request.setBuvid3("buvid3");

        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = Document.builder()
                .text("原始字幕")
                .metadata(new HashMap<>())
                .metadata("title", "测试标题")
                .metadata("description", "测试描述")
                .build();
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
            verify(videoMapper, never()).insert(any(Video.class));
            verify(tokenTextSplitter, never()).apply(any());
            verify(dashVectorStore, never()).add(any());
            verify(videoStatusWriter, never()).markFailed(any(Video.class), any());
        }
    }

    /**
     * 暴露 Bug：@Transactional + catch-rethrow 导致 FAILED 状态丢失
     *
     * <p>场景：dashVectorStore.add() 在写入向量时抛出异常。
     * catch 块捕获后将视频状态更新为 FAILED 并调用 videoMapper.update()，
     * 然后重新抛出 BusinessException。
     *
     * <p>由于方法标注了 @Transactional，Spring 在异常传播后会回滚整个事务，
     * 包括 videoMapper.insert()（创建视频记录）和 videoMapper.update()（写 FAILED 状态）。
     * 最终数据库里什么也没有——用户只看到报错，视频列表里查不到任何记录。
     *
     * <p>此测试验证：update() 确实被调用且携带 FAILED 状态（证明代码意图写入失败记录），
     * 但在真实事务环境下该调用会被回滚，意图与实际行为矛盾。
     * 修复方式：将失败状态更新放到 REQUIRES_NEW 独立事务中。
     */
    @Test
    void importVideoFailure_failedStatusUpdateIsCalledButWouldBeRolledBackByTransaction() {
        ImportVideoRequest request = new ImportVideoRequest();
        request.setBvidOrUrl("BV1KMwgeKECx");
        request.setSessdata("sessdata");
        request.setBiliJct("biliJct");
        request.setBuvid3("buvid3");

        when(videoMapper.selectByUserIdAndBvid(1L, "BV1KMwgeKECx")).thenReturn(null);

        Document sourceDocument = Document.builder()
                .text("视频原文")
                .metadata(new HashMap<>())
                .metadata("title", "测试标题")
                .metadata("description", "测试描述")
                .build();
        Document splitDocument = Document.builder()
                .text("切分片段")
                .metadata(new HashMap<>())
                .build();

        when(subtitleCleaningTransformer.apply(List.of(sourceDocument))).thenReturn(List.of(sourceDocument));
        when(tokenTextSplitter.apply(List.of(sourceDocument))).thenReturn(List.of(splitDocument));

        // insert() 模拟给 video 赋 ID，与正常流程一致
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

        // 模拟向量写入失败
        doThrow(new RuntimeException("DashVector 写入失败")).when(dashVectorStore).add(any());

        try (MockedConstruction<BilibiliDocumentReader> ignored = mockConstruction(
                BilibiliDocumentReader.class,
                (mock, context) -> when(mock.get()).thenReturn(List.of(sourceDocument)))) {

            // 方法应抛出 BusinessException（catch 块重新抛出）
            assertThrows(BusinessException.class, () -> videoService.importVideo(request, 1L));

            // 验证修复后的行为：catch 块通过 VideoStatusWriter.markFailed() 写入失败状态
            // markFailed() 运行在 REQUIRES_NEW 独立事务中，不受外层事务回滚影响
            ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
            verify(videoStatusWriter, times(1)).markFailed(
                    videoCaptor.capture(),
                    eq("DashVector 写入失败")
            );
            assertEquals(
                    100L,
                    videoCaptor.getValue().getId(),
                    "传给 markFailed 的 video 应是已 insert 并拿到 ID 的那个对象"
            );
        }
    }
}
