package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliCredentials;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliResource;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitlePage;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitleTrack;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliVideoSubtitles;
import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import com.example.ragbilibili.config.SubtitleProbeProperties;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.VideoStatus;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import com.example.ragbilibili.probe.PlaywrightSubtitleProbeService;
import com.example.ragbilibili.probe.SubtitleProbeResult;
import com.example.ragbilibili.service.VideoService;
import com.example.ragbilibili.transformer.SubtitleCleaningTransformer;
import com.example.ragbilibili.transformer.SubtitleCueChunker;
import com.example.ragbilibili.transformer.TimestampedSubtitleChunk;
import com.example.ragbilibili.util.BVIDParser;
import com.example.ragbilibili.util.VectorIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private SubtitleCleaningTransformer subtitleCleaningTransformer;

    @Autowired
    private SubtitleCueChunker subtitleCueChunker;

    @Autowired
    private DashVectorStore dashVectorStore;

    @Autowired
    private VideoStatusWriter videoStatusWriter;

    @Autowired
    private VideoImportTxService videoImportTxService;

    @Autowired
    private VideoDeleteTxService videoDeleteTxService;

    @Autowired
    private PlaywrightSubtitleProbeService subtitleProbeService;

    @Autowired
    private SubtitleProbeProperties subtitleProbeProperties;

    @Override
    public VideoResponse importVideo(ImportVideoRequest request, Long userId) {
        String bvid = BVIDParser.parse(request.getBvidOrUrl());
        Video video = null;
        PreparedVideoImportData prepared = null;
        boolean vectorWritten = false;

        try {
            prepared = prepareImportData(request, userId, bvid);
            video = videoImportTxService.createImportingVideo(prepared, userId);

            dashVectorStore.add(prepared.getIndexedDocuments());
            vectorWritten = true;

            videoImportTxService.finalizeImportSuccess(video, userId, prepared);

            log.info("视频导入成功: userId={}, bvid={}, chunks={}", userId, bvid, prepared.getChunkPayloads().size());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("视频导入失败: userId={}, bvid={}", userId, bvid, e);
            handleImportFailure(video, prepared, vectorWritten, e);
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
    public void deleteVideo(Long videoId, Long userId) {
        List<String> vectorIds = videoDeleteTxService.deleteVideoData(videoId, userId);

        if (!vectorIds.isEmpty()) {
            try {
                dashVectorStore.delete(vectorIds);
                log.info("视频向量删除成功: userId={}, videoId={}, vectorCount={}", userId, videoId, vectorIds.size());
            } catch (RuntimeException e) {
                log.error("视频DB已删除，DashVector删除失败，向量可能残留: userId={}, videoId={}, vectorCount={}",
                        userId, videoId, vectorIds.size(), e);
            }
        }

        log.info("视频删除流程完成: userId={}, videoId={}", userId, videoId);
    }

    private PreparedVideoImportData prepareImportData(ImportVideoRequest request, Long userId, String bvid) {
        BilibiliCredentials credentials = BilibiliCredentials.builder()
                .sessdata(request.getSessdata())
                .biliJct(request.getBiliJct())
                .buvid3(request.getBuvid3())
                .build();

        BilibiliResource resource = new BilibiliResource(bvid, credentials);
        BilibiliVideoSubtitles subtitles = loadSubtitlesWithProbeAndRetry(resource, credentials, bvid);
        List<TimestampedSubtitleChunk> chunks = buildTimestampedChunks(subtitles);
        if (chunks.isEmpty()) {
            throw cleanedSubtitleEmptyException();
        }

        return buildPreparedImportData(userId, subtitles, chunks);
    }

    private BilibiliVideoSubtitles loadSubtitlesWithProbeAndRetry(BilibiliResource resource,
                                                                  BilibiliCredentials credentials,
                                                                  String bvid) {
        BilibiliVideoSubtitles subtitles = readSubtitles(resource);

        if (!hasSubtitleCues(subtitles)) {
            SubtitleProbeResult probeResult = subtitleProbeService.probe(buildVideoPageUrl(bvid), credentials);
            log.info("字幕探测结果: bvid={}, status={}, reason={}", bvid, probeResult.getStatus(), probeResult.getReason());

            if (probeResult.hasNoSubtitleButton()) {
                throw noOfficialSubtitleException();
            }

            subtitles = retryReadSubtitles(resource, bvid);
            if (!hasSubtitleCues(subtitles)) {
                if (probeResult.hasSubtitleButton()) {
                    throw subtitleTemporarilyUnavailableException();
                }
                throw subtitleUnavailableAfterRetryException();
            }
        }

        return subtitles;
    }

    private PreparedVideoImportData buildPreparedImportData(Long userId,
                                                            BilibiliVideoSubtitles subtitles,
                                                            List<TimestampedSubtitleChunk> chunks) {
        List<Document> indexedDocuments = new ArrayList<>(chunks.size());
        List<String> vectorIds = new ArrayList<>(chunks.size());
        List<PreparedVideoImportData.PreparedChunkPayload> chunkPayloads = new ArrayList<>(chunks.size());
        int totalChunks = chunks.size();

        for (int i = 0; i < chunks.size(); i++) {
            TimestampedSubtitleChunk chunk = chunks.get(i);
            String vectorId = VectorIDGenerator.generate(userId, subtitles.bvid(), i);

            Document indexedDocument = Document.builder()
                    .id(vectorId)
                    .text(chunk.text())
                    .metadata("userId", userId)
                    .metadata("bvid", subtitles.bvid())
                    .metadata("chunkIndex", i)
                    .metadata("cid", chunk.cid())
                    .metadata("pageNumber", chunk.pageNumber())
                    .metadata("startTimeMs", chunk.startTimeMs())
                    .metadata("endTimeMs", chunk.endTimeMs())
                    .metadata("subtitleLanguage", chunk.subtitleLanguage())
                    .build();
            indexedDocuments.add(indexedDocument);
            vectorIds.add(vectorId);
            chunkPayloads.add(new PreparedVideoImportData.PreparedChunkPayload(
                    i,
                    totalChunks,
                    indexedDocument.getText(),
                    vectorId,
                    chunk.cid(),
                    chunk.pageNumber(),
                    chunk.startTimeMs(),
                    chunk.endTimeMs(),
                    chunk.subtitleLanguage()
            ));
        }

        return new PreparedVideoImportData(
                subtitles.bvid(),
                subtitles.title(),
                subtitles.description(),
                indexedDocuments,
                vectorIds,
                chunkPayloads
        );
    }

    private void handleImportFailure(Video video,
                                     PreparedVideoImportData prepared,
                                     boolean vectorWritten,
                                     Exception exception) {
        if (vectorWritten && prepared != null && !prepared.getVectorIds().isEmpty()) {
            try {
                dashVectorStore.delete(prepared.getVectorIds());
            } catch (Exception deleteException) {
                log.error("向量补偿删除失败: bvid={}", prepared.getBvid(), deleteException);
            }
        }

        if (video != null) {
            videoStatusWriter.markFailed(video, exception.getMessage());
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

        int chunkCount = chunkMapper.countByVideoId(video.getId());
        response.setChunkCount(chunkCount);

        return response;
    }

    private List<TimestampedSubtitleChunk> buildTimestampedChunks(BilibiliVideoSubtitles subtitles) {
        List<TimestampedSubtitleChunk> chunks = new ArrayList<>();
        for (BilibiliSubtitlePage page : subtitles.pages()) {
            if (page.tracks().isEmpty()) {
                continue;
            }
            BilibiliSubtitleTrack track = page.tracks().get(0);
            chunks.addAll(subtitleCueChunker.split(page, track, subtitleCleaningTransformer.cleanCues(track.cues())));
        }
        return chunks;
    }

    private BilibiliVideoSubtitles readSubtitles(BilibiliResource resource) {
        List<BilibiliVideoSubtitles> videos = new BilibiliDocumentReader(resource).readSubtitles();
        return videos.isEmpty() ? null : videos.get(0);
    }

    private BilibiliVideoSubtitles retryReadSubtitles(BilibiliResource resource, String bvid) {
        long[] retryDelaysMillis = subtitleProbeProperties.getRetryDelaysMillis();
        BilibiliVideoSubtitles subtitles = null;
        for (int i = 0; i < retryDelaysMillis.length; i++) {
            sleepQuietly(retryDelaysMillis[i]);
            subtitles = readSubtitles(resource);
            boolean success = hasSubtitleCues(subtitles);
            log.info("字幕重试结果: bvid={}, attempt={}, success={}", bvid, i + 1, success);
            if (success) {
                return subtitles;
            }
        }
        return subtitles;
    }

    private boolean hasSubtitleCues(BilibiliVideoSubtitles subtitles) {
        if (subtitles == null) {
            return false;
        }
        return subtitles.pages().stream()
                .anyMatch(page -> !page.tracks().isEmpty() && !page.tracks().get(0).cues().isEmpty());
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
