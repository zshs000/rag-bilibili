package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.dto.response.MessageResponse;
import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.MessageSourceMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.service.MessageService;
import com.example.ragbilibili.service.CitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {
    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private MessageSourceMapper messageSourceMapper;

    @Autowired
    private CitationService citationService;

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
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        List<MessageSource> allSources = messageSourceMapper.selectByMessageIds(messageIds);
        Map<Long, List<MessageSource>> sourcesByMessageId = allSources == null
                ? Collections.emptyMap()
                : allSources.stream().collect(Collectors.groupingBy(MessageSource::getMessageId));
        return messages.stream()
                .map(message -> convertToResponse(
                        message,
                        sourcesByMessageId.getOrDefault(message.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private MessageResponse convertToResponse(Message message, List<MessageSource> sources) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCreateTime(message.getCreateTime().format(FORMATTER));
        response.setSources(citationService.toResponses(sources));
        return response;
    }
}
