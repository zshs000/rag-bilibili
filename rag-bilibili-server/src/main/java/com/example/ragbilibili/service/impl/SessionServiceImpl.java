package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.request.CreateSessionRequest;
import com.example.ragbilibili.dto.response.SessionResponse;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.SessionType;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import com.example.ragbilibili.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessionServiceImpl implements SessionService {
    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private MessageMapper messageMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public SessionResponse createSession(CreateSessionRequest request, Long userId) {
        // 验证会话类型
        String sessionType = request.getSessionType();
        if (!sessionType.equals(SessionType.SINGLE_VIDEO.getCode()) &&
            !sessionType.equals(SessionType.ALL_VIDEOS.getCode())) {
            throw new BusinessException(ErrorCode.SESSION_TYPE_ERROR);
        }

        // 如果是单视频对话，验证视频ID
        if (sessionType.equals(SessionType.SINGLE_VIDEO.getCode())) {
            if (request.getVideoId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            Video video = videoMapper.selectById(request.getVideoId());
            if (video == null || !video.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
            }
        }

        // 创建会话
        Session session = new Session();
        session.setUserId(userId);
        session.setSessionType(sessionType);
        session.setVideoId(request.getVideoId());
        session.setCreateTime(LocalDateTime.now());

        sessionMapper.insert(session);

        return convertToResponse(session);
    }

    @Override
    public List<SessionResponse> listSessions(Long userId) {
        List<Session> sessions = sessionMapper.selectByUserId(userId);
        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SessionResponse getSession(Long sessionId, Long userId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return convertToResponse(session);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        // 验证会话是否存在且属于当前用户
        Session session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 删除会话关联的消息
        messageMapper.deleteBySessionId(sessionId);

        // 删除会话
        sessionMapper.deleteById(sessionId);
    }

    private SessionResponse convertToResponse(Session session) {
        SessionResponse response = new SessionResponse();
        response.setId(session.getId());
        response.setSessionType(session.getSessionType());
        response.setVideoId(session.getVideoId());
        response.setCreateTime(session.getCreateTime().format(FORMATTER));

        // 如果是单视频对话，查询视频标题
        if (session.getVideoId() != null) {
            Video video = videoMapper.selectById(session.getVideoId());
            if (video != null) {
                response.setVideoTitle(video.getTitle());
            }
        }

        return response;
    }
}
