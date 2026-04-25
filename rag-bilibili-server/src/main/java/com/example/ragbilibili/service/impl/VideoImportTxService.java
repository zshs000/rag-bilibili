package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Chunk;
import com.example.ragbilibili.entity.VectorMapping;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.VideoStatus;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 字幕导入数据库事务服务。
 */
@Service
public class VideoImportTxService {

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private VectorMappingMapper vectorMappingMapper;

    @Transactional
    public Video createImportingVideo(PreparedVideoImportData prepared, Long userId) {
        Video existingVideo = videoMapper.selectByUserIdAndBvid(userId, prepared.getBvid());
        if (existingVideo != null) {
            throw new BusinessException(ErrorCode.VIDEO_ALREADY_EXISTS);
        }

        Video video = new Video();
        video.setUserId(userId);
        video.setBvid(prepared.getBvid());
        video.setTitle(prepared.getTitle());
        video.setDescription(prepared.getDescription());
        video.setStatus(VideoStatus.IMPORTING.getCode());
        video.setImportTime(LocalDateTime.now());
        videoMapper.insert(video);
        return video;
    }

    @Transactional
    public void finalizeImportSuccess(Video video, Long userId, PreparedVideoImportData prepared) {
        LocalDateTime now = LocalDateTime.now();
        List<Chunk> chunks = new ArrayList<>(prepared.getChunkPayloads().size());
        for (PreparedVideoImportData.PreparedChunkPayload payload : prepared.getChunkPayloads()) {
            Chunk chunk = new Chunk();
            chunk.setVideoId(video.getId());
            chunk.setUserId(userId);
            chunk.setBvid(prepared.getBvid());
            chunk.setTitle(prepared.getTitle());
            chunk.setChunkIndex(payload.getChunkIndex());
            chunk.setTotalChunks(payload.getTotalChunks());
            chunk.setChunkText(payload.getChunkText());
            chunk.setCreateTime(now);
            chunks.add(chunk);
        }

        if (!chunks.isEmpty()) {
            chunkMapper.batchInsert(chunks);
        }

        List<VectorMapping> mappings = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            PreparedVideoImportData.PreparedChunkPayload payload = prepared.getChunkPayloads().get(i);

            VectorMapping mapping = new VectorMapping();
            mapping.setUserId(userId);
            mapping.setVideoId(video.getId());
            mapping.setChunkId(chunk.getId());
            mapping.setVectorId(payload.getVectorId());
            mapping.setCreateTime(now);
            mappings.add(mapping);
        }

        if (!mappings.isEmpty()) {
            vectorMappingMapper.batchInsert(mappings);
        }

        video.setStatus(VideoStatus.SUCCESS.getCode());
        video.setFailReason(null);
        videoMapper.update(video);
    }
}