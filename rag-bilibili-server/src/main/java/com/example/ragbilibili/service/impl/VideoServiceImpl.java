package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliCredentials;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliResource;
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
import com.example.ragbilibili.mapper.*;
import com.example.ragbilibili.probe.PlaywrightSubtitleProbeService;
import com.example.ragbilibili.probe.SubtitleProbeResult;
import com.example.ragbilibili.service.VideoService;
import com.example.ragbilibili.transformer.SubtitleCleaningTransformer;
import com.example.ragbilibili.util.BVIDParser;
import com.example.ragbilibili.util.VectorIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);
    private static final String TRANSCRIPT_MARKER = "Transcript:";
    private static final String SUBTITLE_SEGMENT_COUNT = "subtitle_segment_count";

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private VectorMappingMapper vectorMappingMapper;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    @Autowired
    private SubtitleCleaningTransformer subtitleCleaningTransformer;

    @Autowired
    private DashVectorStore dashVectorStore;

    @Autowired
    private VideoStatusWriter videoStatusWriter;

    @Autowired
    private PlaywrightSubtitleProbeService subtitleProbeService;

    @Autowired
    private SubtitleProbeProperties subtitleProbeProperties;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public VideoResponse importVideo(ImportVideoRequest request, Long userId) {
        // 1. 解析 BV 号
        String bvid = BVIDParser.parse(request.getBvidOrUrl());

        // 2. 检查视频是否已存在
        Video existingVideo = videoMapper.selectByUserIdAndBvid(userId, bvid);
        if (existingVideo != null) {
            throw new BusinessException(ErrorCode.VIDEO_ALREADY_EXISTS);
        }

        Video video = null;

        try {
            // 3. 使用 BilibiliDocumentReader 读取视频内容
            BilibiliCredentials credentials = BilibiliCredentials.builder()
                    .sessdata(request.getSessdata())
                    .biliJct(request.getBiliJct())
                    .buvid3(request.getBuvid3())
                    .build();

            BilibiliResource resource = new BilibiliResource(bvid, credentials);
            List<Document> documents = readDocuments(resource);

            if (documents.isEmpty()) {
                SubtitleProbeResult probeResult = subtitleProbeService.probe(buildVideoPageUrl(bvid), credentials);
                log.info("字幕探测结果: bvid={}, status={}, reason={}", bvid, probeResult.getStatus(), probeResult.getReason());

                if (probeResult.hasNoSubtitleButton()) {
                    throw noOfficialSubtitleException();
                }

                documents = retryReadDocuments(resource, bvid);
                if (documents.isEmpty()) {
                    if (probeResult.hasSubtitleButton()) {
                        throw subtitleTemporarilyUnavailableException();
                    }
                    throw subtitleUnavailableAfterRetryException();
                }
            }

            Document document = documents.get(0);
            String videoTitle = (String) document.getMetadata().get("title");
            String videoDescription = (String) document.getMetadata().get("description");

            // 4. 先清洗自动字幕，清洗后若已无有效字幕则直接返回无字幕错误
            List<Document> cleanedDocuments = subtitleCleaningTransformer.apply(documents);
            if (!hasUsableSubtitleContent(cleanedDocuments)) {
                throw cleanedSubtitleEmptyException();
            }

            // 5. 创建视频记录（状态：IMPORTING）
            video = new Video();
            video.setUserId(userId);
            video.setBvid(bvid);
            video.setTitle(videoTitle);
            video.setDescription(videoDescription);
            video.setStatus(VideoStatus.IMPORTING.getCode());
            video.setImportTime(LocalDateTime.now());
            videoMapper.insert(video);

            // 6. 按 token 切分清洗后的字幕，避免广告和低信息噪声进入向量库
            List<Document> splitDocuments = tokenTextSplitter.apply(cleanedDocuments);
            List<Document> indexedDocuments = new ArrayList<>(splitDocuments.size());

            // 7. 生成向量ID并准备数据
            List<Chunk> chunks = new ArrayList<>();
            List<VectorMapping> mappings = new ArrayList<>();
            int totalChunks = splitDocuments.size();

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document doc = splitDocuments.get(i);
                String vectorId = VectorIDGenerator.generate(userId, bvid, i);

                Document indexedDocument = Document.builder()
                        .id(vectorId)
                        .text(doc.getText())
                        .metadata(new HashMap<>(doc.getMetadata()))
                        .metadata("userId", userId)
                        .metadata("bvid", bvid)
                        .metadata("chunkIndex", i)
                        .build();
                indexedDocuments.add(indexedDocument);

                // 创建分片记录
                Chunk chunk = new Chunk();
                chunk.setVideoId(video.getId());
                chunk.setUserId(userId);
                chunk.setBvid(bvid);
                chunk.setTitle(videoTitle);
                chunk.setChunkIndex(i);
                chunk.setTotalChunks(totalChunks);
                chunk.setChunkText(indexedDocument.getText());
                chunk.setCreateTime(LocalDateTime.now());
                chunks.add(chunk);
            }

            // 8. 批量插入分片
            if (!chunks.isEmpty()) {
                chunkMapper.batchInsert(chunks);
            }

            // 9. 写入 DashVector
            dashVectorStore.add(indexedDocuments);

            // 10. 创建向量映射
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                String vectorId = VectorIDGenerator.generate(userId, bvid, i);

                VectorMapping mapping = new VectorMapping();
                mapping.setUserId(userId);
                mapping.setVideoId(video.getId());
                mapping.setChunkId(chunk.getId());
                mapping.setVectorId(vectorId);
                mapping.setCreateTime(LocalDateTime.now());
                mappings.add(mapping);
            }

            // 11. 批量插入向量映射
            if (!mappings.isEmpty()) {
                vectorMappingMapper.batchInsert(mappings);
            }

            // 12. 更新视频状态为成功
            video.setStatus(VideoStatus.SUCCESS.getCode());
            videoMapper.update(video);

            log.info("视频导入成功: userId={}, bvid={}, chunks={}", userId, bvid, totalChunks);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("视频导入失败: userId={}, bvid={}", userId, bvid, e);

            if (video != null) {
                // 用独立事务写入失败状态，防止被外层事务回滚
                videoStatusWriter.markFailed(video, e.getMessage());
            }

            throw new BusinessException(ErrorCode.VIDEO_IMPORT_FAILED);
        }

        return convertToResponse(video);
    }

    @Override
    public List<VideoResponse> listVideos(Long userId) {
        List<Video> videos = videoMapper.selectByUserId(userId);
        return videos.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public VideoResponse getVideo(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return convertToResponse(video);
    }

    @Override
    @Transactional
    public void deleteVideo(Long videoId, Long userId) {
        // 1. 验证视频是否存在且属于当前用户
        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        try {
            // 2. 查询向量ID列表
            List<String> vectorIds = vectorMappingMapper.selectVectorIdsByVideoId(videoId);

            // 3. 从 DashVector 删除向量（基于ID删除）
            if (!vectorIds.isEmpty()) {
                dashVectorStore.delete(vectorIds);
            }

            // 4. 查询关联的会话ID列表
            List<Long> sessionIds = sessionMapper.selectIdsByVideoId(videoId);

            // 5. 删除会话关联的消息
            if (!sessionIds.isEmpty()) {
                messageMapper.deleteBySessionIds(sessionIds);
            }

            // 6. 删除会话
            sessionMapper.deleteByVideoId(videoId);

            // 7. 删除向量映射
            vectorMappingMapper.deleteByVideoId(videoId);

            // 8. 删除分片
            chunkMapper.deleteByVideoId(videoId);

            // 9. 删除视频记录
            videoMapper.deleteById(videoId);

            log.info("视频删除成功: userId={}, videoId={}, bvid={}", userId, videoId, video.getBvid());

        } catch (Exception e) {
            log.error("视频删除失败: userId={}, videoId={}", userId, videoId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private VideoResponse convertToResponse(Video video) {
        VideoResponse response = new VideoResponse();
        response.setId(video.getId());
        response.setBvid(video.getBvid());
        response.setTitle(video.getTitle());
        response.setDescription(video.getDescription());
        response.setImportTime(video.getImportTime().format(FORMATTER));
        response.setStatus(video.getStatus());
        response.setFailReason(video.getFailReason());

        // 查询分片数量
        int chunkCount = chunkMapper.countByVideoId(video.getId());
        response.setChunkCount(chunkCount);

        return response;
    }

    private boolean hasUsableSubtitleContent(List<Document> documents) {
        for (Document document : documents) {
            Object segmentCount = document.getMetadata().get(SUBTITLE_SEGMENT_COUNT);
            if (segmentCount instanceof Number number) {
                if (number.intValue() > 0) {
                    return true;
                }
                continue;
            }

            String text = document.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            int markerIndex = text.indexOf(TRANSCRIPT_MARKER);
            if (markerIndex < 0) {
                return true;
            }

            String transcript = text.substring(markerIndex + TRANSCRIPT_MARKER.length()).trim();
            if (!transcript.isBlank()) {
                return true;
            }
        }

        return false;
    }

    private List<Document> readDocuments(BilibiliResource resource) {
        return new BilibiliDocumentReader(resource).get();
    }

    private List<Document> retryReadDocuments(BilibiliResource resource, String bvid) {
        long[] retryDelaysMillis = subtitleProbeProperties.getRetryDelaysMillis();
        List<Document> documents = List.of();
        for (int i = 0; i < retryDelaysMillis.length; i++) {
            sleepQuietly(retryDelaysMillis[i]);
            documents = readDocuments(resource);
            log.info("字幕重试结果: bvid={}, attempt={}, success={}", bvid, i + 1, !documents.isEmpty());
            if (!documents.isEmpty()) {
                return documents;
            }
        }
        return documents;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildVideoPageUrl(String bvid) {
        return "https://www.bilibili.com/video/" + bvid + "/";
    }

    private BusinessException noOfficialSubtitleException() {
        return new BusinessException(
                ErrorCode.VIDEO_NO_SUBTITLE.getCode(),
                "未检测到 B 站官方字幕（含 AI 字幕）。请先去视频主页确认播放器右下角是否有“字幕”按钮；若没有，则当前视频暂不支持导入。"
        );
    }

    private BusinessException cleanedSubtitleEmptyException() {
        return new BusinessException(
                ErrorCode.VIDEO_NO_SUBTITLE.getCode(),
                "已读取到字幕，但清洗后未保留有效内容，当前视频暂不支持导入。"
        );
    }

    private BusinessException subtitleTemporarilyUnavailableException() {
        return new BusinessException(
                ErrorCode.VIDEO_NO_SUBTITLE.getCode(),
                "已检测到视频主页存在“字幕”按钮，但当前官方字幕接口暂未返回内容，可能仍在处理或发生了短暂波动，请稍后重试。"
        );
    }

    private BusinessException subtitleUnavailableAfterRetryException() {
        return new BusinessException(
                ErrorCode.VIDEO_NO_SUBTITLE.getCode(),
                "未读取到可用字幕。请先前往视频主页确认播放器右下角是否存在“字幕”按钮；若没有，则当前视频大概率未开通 B 站官方字幕。"
        );
    }
}
