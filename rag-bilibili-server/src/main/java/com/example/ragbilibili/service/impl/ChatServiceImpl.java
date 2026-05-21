package com.example.ragbilibili.service.impl;

import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import com.example.ragbilibili.dto.sse.SseContentEvent;
import com.example.ragbilibili.dto.sse.SseEndEvent;
import com.example.ragbilibili.dto.sse.SseErrorEvent;
import com.example.ragbilibili.dto.sse.SseStartEvent;
import com.example.ragbilibili.entity.Message;
import com.example.ragbilibili.entity.Session;
import com.example.ragbilibili.entity.Video;
import com.example.ragbilibili.enums.MessageRole;
import com.example.ragbilibili.enums.SessionType;
import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.mapper.MessageMapper;
import com.example.ragbilibili.mapper.SessionMapper;
import com.example.ragbilibili.mapper.VideoMapper;
import com.example.ragbilibili.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private DashVectorStore dashVectorStore;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("taskExecutor")
    private TaskExecutor taskExecutor;

    private static final int TOP_K = 5;
    private static final long SSE_TIMEOUT = 60000L;
    private static final int MAX_HISTORY = 10;

    @Override
    public SseEmitter streamMessage(Long sessionId, String content, Long userId) {
        // 1. 验证会话
        Session session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 2. 保存用户消息
        Message userMessage = new Message();
        userMessage.setSessionId(sessionId);
        userMessage.setRole(MessageRole.USER.getCode());
        userMessage.setContent(content);
        userMessage.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMessage);

        // 3. 创建 SSE Emitter
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 4. 使用TaskExecutor异步处理
        taskExecutor.execute(() -> {
            try {
                // 5. 发送start事件
                SseStartEvent startEvent = new SseStartEvent(userMessage.getId());
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data(objectMapper.writeValueAsString(startEvent)));

                // 6. RAG 检索
                List<Document> relevantDocs = retrieveRelevantDocuments(session, content, userId);

                // 7. 构建上下文
                String context = buildContext(relevantDocs);

                // 8. 获取历史消息（排除刚插入的当前用户消息）
                List<Message> historyMessages = messageMapper.selectBySessionId(sessionId);
                List<org.springframework.ai.chat.messages.Message> springAiMessages =
                        buildMessageHistory(historyMessages, userMessage.getId());

                // 9. 构建提示词
                String systemPrompt = buildSystemPrompt(context);

                // 10. 流式调用 LLM
                ChatClient chatClient = chatClientBuilder.build();
                Flux<ChatResponse> responseFlux = chatClient.prompt()
                        .system(systemPrompt)
                        .messages(springAiMessages)
                        .user(content)
                        .stream()
                        .chatResponse();

                // 11. 收集完整响应
                StringBuilder fullResponse = new StringBuilder();

                // 12. 流式发送
                responseFlux.subscribe(
                        response -> {
                            String chunk = response.getResult().getOutput().getText();
                            if (chunk != null && !chunk.isEmpty()) {
                                fullResponse.append(chunk);
                                try {
                                    SseContentEvent contentEvent = new SseContentEvent(chunk);
                                    emitter.send(SseEmitter.event()
                                            .name("content")
                                            .data(objectMapper.writeValueAsString(contentEvent)));
                                } catch (Exception e) {
                                    log.error("SSE发送失败", e);
                                    emitter.completeWithError(e);
                                }
                            }
                        },
                        error -> {
                            log.error("LLM调用失败", error);
                            try {
                                SseErrorEvent errorEvent = new SseErrorEvent(
                                        error.getMessage() != null ? error.getMessage() : "未知错误");
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(objectMapper.writeValueAsString(errorEvent)));
                            } catch (Exception e) {
                                log.error("发送错误事件失败", e);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                // 13. 保存助手消息
                                Message assistantMessage = new Message();
                                assistantMessage.setSessionId(sessionId);
                                assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
                                assistantMessage.setContent(fullResponse.toString());
                                assistantMessage.setCreateTime(LocalDateTime.now());
                                messageMapper.insert(assistantMessage);

                                // 14. 发送end事件
                                SseEndEvent endEvent = new SseEndEvent(
                                        assistantMessage.getId(),
                                        fullResponse.toString());
                                emitter.send(SseEmitter.event()
                                        .name("end")
                                        .data(objectMapper.writeValueAsString(endEvent)));

                                // 15. 完成 SSE
                                emitter.complete();
                                log.info("对话完成: sessionId={}, userId={}", sessionId, userId);
                            } catch (Exception e) {
                                log.error("发送end事件失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                log.error("对话处理失败: sessionId={}, userId={}", sessionId, userId, e);
                try {
                    SseErrorEvent errorEvent = new SseErrorEvent(
                            e.getMessage() != null ? e.getMessage() : "处理失败");
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(errorEvent)));
                } catch (Exception ex) {
                    log.error("发送错误事件失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 检索相关文档
     */
    private List<Document> retrieveRelevantDocuments(Session session, String query, Long userId) {
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();

        if (session.getSessionType().equals(SessionType.SINGLE_VIDEO.getCode())) {
            Video video = videoMapper.selectById(session.getVideoId());
            if (video == null) {
                return List.of();
            }

            return dashVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(TOP_K)
                            .similarityThreshold(0.0)
                            .filterExpression(
                                    filterExpressionBuilder.and(
                                            filterExpressionBuilder.eq("userId", userId),
                                            filterExpressionBuilder.eq("bvid", video.getBvid())
                                    ).build()
                            )
                            .build()
            );
        } else {
            return dashVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(TOP_K)
                            .similarityThreshold(0.0)
                            .filterExpression(filterExpressionBuilder.eq("userId", userId).build())
                            .build()
            );
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "没有找到相关的视频内容。";
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是相关的视频内容：\n\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append(String.format("[片段 %d]\n", i + 1));
            context.append(doc.getText());
            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * 构建聊天历史（滑动窗口，转换为 Spring AI Message 类型）
     */
    private List<org.springframework.ai.chat.messages.Message> buildMessageHistory(
            List<Message> messages, Long excludeId) {
        List<Message> filtered = messages.stream()
                .filter(m -> !m.getId().equals(excludeId))
                .sorted(Comparator.comparing(Message::getCreateTime))
                .collect(Collectors.toList());

        int start = Math.max(0, filtered.size() - MAX_HISTORY);
        return filtered.subList(start, filtered.size()).stream()
                .map(m -> m.getRole().equals(MessageRole.USER.getCode())
                        ? new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .collect(Collectors.toList());
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String context) {
        return String.format(
                "你是一个基于B站视频内容的智能问答助手。\n\n" +
                "你的任务是根据提供的视频内容片段，准确、详细地回答用户的问题。\n\n" +
                "注意事项：\n" +
                "1. 仅基于提供的视频内容回答问题\n" +
                "2. 如果视频内容中没有相关信息，请明确告知用户\n" +
                "3. 回答要准确、简洁、有条理\n" +
                "4. 可以引用具体的片段内容来支持你的回答\n\n" +
                "%s",
                context
        );
    }
}
