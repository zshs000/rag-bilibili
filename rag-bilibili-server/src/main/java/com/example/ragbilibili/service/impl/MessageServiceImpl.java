package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {
    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private SessionMapper sessionMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<MessageResponse> listMessages(Long sessionId, Long userId) {
        // 验证会话是否存在且属于当前用户
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 查询消息列表
        List<Message> messages = messageMapper.selectBySessionId(sessionId);
        return messages.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private MessageResponse convertToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCreateTime(message.getCreateTime().format(FORMATTER));
        return response;
    }
}
