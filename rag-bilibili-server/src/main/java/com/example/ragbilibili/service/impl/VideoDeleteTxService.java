package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.ChunkMapper;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 视频删除数据库事务服务。
 */
@Service
public class VideoDeleteTxService {

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> deleteVideoData(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        List<String> vectorIds = vectorMappingMapper.selectVectorIdsByVideoId(videoId);
        List<Long> sessionIds = sessionMapper.selectIdsByVideoId(videoId);
        if (sessionIds == null) {
            sessionIds = List.of();
        }
        if (!sessionIds.isEmpty()) {
            messageMapper.deleteBySessionIds(sessionIds);
        }

        sessionMapper.deleteByVideoId(videoId);
        vectorMappingMapper.deleteByVideoId(videoId);
        chunkMapper.deleteByVideoId(videoId);
        videoMapper.deleteById(videoId);

        return vectorIds == null ? Collections.emptyList() : vectorIds;
    }
}
