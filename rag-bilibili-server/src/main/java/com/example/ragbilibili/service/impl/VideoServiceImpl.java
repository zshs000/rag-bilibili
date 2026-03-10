package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliCredentials;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliDocumentReader;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliResource;
import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
import com.example.ragbilibili.dto.request.ImportVideoRequest;
import com.example.ragbilibili.dto.response.VideoResponse;
import com.example.ragbilibili.entity.Chunk;
import com.example.ragbilibili.entity.VectorMapping;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.VideoStatus;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.*;
import com.example.ragbilibili.service.VideoService;
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
    private DashVectorStore dashVectorStore;

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

        // 3. 创建视频记录（状态：IMPORTING）
        Video video = new Video();
        video.setUserId(userId);
        video.setBvid(bvid);
        video.setStatus(VideoStatus.IMPORTING.getCode());
        video.setImportTime(LocalDateTime.now());
        videoMapper.insert(video);

        try {
            // 4. 使用 BilibiliDocumentReader 读取视频内容
            BilibiliCredentials credentials = BilibiliCredentials.builder()
                    .sessdata(request.getSessdata())
                    .biliJct(request.getBiliJct())
                    .buvid3(request.getBuvid3())
                    .build();

            BilibiliDocumentReader reader = new BilibiliDocumentReader(new BilibiliResource(bvid, credentials));
            List<Document> documents = reader.get();

            if (documents.isEmpty()) {
                throw new BusinessException(ErrorCode.VIDEO_NO_SUBTITLE);
            }

            Document document = documents.get(0);
            String videoTitle = (String) document.getMetadata().get("title");
            String videoDescription = (String) document.getMetadata().get("description");

            // 更新视频信息
            video.setTitle(videoTitle);
            video.setDescription(videoDescription);
            videoMapper.update(video);

            // 5. 文本切分
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);
            List<Document> indexedDocuments = new ArrayList<>(splitDocuments.size());

            // 6. 生成向量ID并准备数据
            List<Chunk> chunks = new ArrayList<>();
            List<VectorMapping> mappings = new ArrayList<>();
            int totalChunks = splitDocuments.size();

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document doc = splitDocuments.get(i);
                String vectorId = VectorIDGenerator.generate(userId, bvid, i);

                Document indexedDocument = Document.builder()
                        .withId(vectorId)
                        .withContent(doc.getContent())
                        .withMetadata(new HashMap<>(doc.getMetadata()))
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
                chunk.setChunkText(indexedDocument.getContent());
                chunk.setCreateTime(LocalDateTime.now());
                chunks.add(chunk);
            }

            // 7. 批量插入分片
            if (!chunks.isEmpty()) {
                chunkMapper.batchInsert(chunks);
            }

            // 8. 写入 DashVector
            dashVectorStore.add(indexedDocuments);

            // 9. 创建向量映射
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

            // 10. 批量插入向量映射
            if (!mappings.isEmpty()) {
                vectorMappingMapper.batchInsert(mappings);
            }

            // 11. 更新视频状态为成功
            video.setStatus(VideoStatus.SUCCESS.getCode());
            videoMapper.update(video);

            log.info("视频导入成功: userId={}, bvid={}, chunks={}", userId, bvid, totalChunks);

        } catch (Exception e) {
            log.error("视频导入失败: userId={}, bvid={}", userId, bvid, e);

            // 更新视频状态为失败
            video.setStatus(VideoStatus.FAILED.getCode());
            video.setFailReason(e.getMessage());
            videoMapper.update(video);

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
}
