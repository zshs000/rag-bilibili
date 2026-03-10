package com.example.ragbilibili.service.impl;

import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
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

    private static final int TOP_K = 5;
    private static final long SSE_TIMEOUT = 60000L;

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

        // 4. 异步处理
        new Thread(() -> {
            try {
                // 5. RAG 检索
                List<Document> relevantDocs = retrieveRelevantDocuments(session, content, userId);

                // 6. 构建上下文
                String context = buildContext(relevantDocs);

                // 7. 获取历史消息
                List<Message> historyMessages = messageMapper.selectBySessionId(sessionId);
                String chatHistory = buildChatHistory(historyMessages);

                // 8. 构建提示词
                String systemPrompt = buildSystemPrompt(context);
                String userPrompt = content;

                // 9. 流式调用 LLM
                ChatClient chatClient = chatClientBuilder.build();
                Flux<ChatResponse> responseFlux = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .stream()
                        .chatResponse();

                // 10. 收集完整响应
                StringBuilder fullResponse = new StringBuilder();

                // 11. 流式发送
                responseFlux.subscribe(
                        response -> {
                            String chunk = response.getResult().getOutput().getContent();
                            if (chunk != null && !chunk.isEmpty()) {
                                fullResponse.append(chunk);
                                try {
                                    emitter.send(SseEmitter.event()
                                            .data(chunk)
                                            .name("message"));
                                } catch (Exception e) {
                                    log.error("SSE发送失败", e);
                                    emitter.completeWithError(e);
                                }
                            }
                        },
                        error -> {
                            log.error("LLM调用失败", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            // 12. 保存助手消息
                            Message assistantMessage = new Message();
                            assistantMessage.setSessionId(sessionId);
                            assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
                            assistantMessage.setContent(fullResponse.toString());
                            assistantMessage.setCreateTime(LocalDateTime.now());
                            messageMapper.insert(assistantMessage);

                            // 13. 完成 SSE
                            emitter.complete();
                            log.info("对话完成: sessionId={}, userId={}", sessionId, userId);
                        }
                );

            } catch (Exception e) {
                log.error("对话处理失败: sessionId={}, userId={}", sessionId, userId, e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 检索相关文档
     */
    private List<Document> retrieveRelevantDocuments(Session session, String query, Long userId) {
        if (session.getSessionType().equals(SessionType.SINGLE_VIDEO.getCode())) {
            // 单视频对话：使用过滤条件
            // DashVector 支持通过 metadata 过滤，但这里我们使用向量ID前缀匹配
            // 向量ID格式：userId:bvid:chunkIndex
            // 我们需要搜索所有 userId:bvid:* 的向量

            // 注意：DashVectorStore 的 similaritySearch 方法可能不直接支持前缀过滤
            // V1 版本简化处理：先检索 TOP_K，然后在应用层过滤
            List<Document> allDocs = dashVectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(TOP_K * 2).withSimilarityThresholdAll()
            );

            Video video = videoMapper.selectById(session.getVideoId());
            if (video == null) {
                return List.of();
            }

            // 过滤出属于当前视频的文档
            String videoPrefix = userId + ":" + video.getBvid() + ":";
            return allDocs.stream()
                    .filter(doc -> doc.getId() != null && doc.getId().startsWith(videoPrefix))
                    .limit(TOP_K)
                    .collect(Collectors.toList());
        } else {
            // 全部视频对话：检索用户所有视频
            List<Document> allDocs = dashVectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(TOP_K * 2).withSimilarityThresholdAll()
            );

            // 过滤出属于当前用户的文档
            String userPrefix = userId + ":";
            return allDocs.stream()
                    .filter(doc -> doc.getId() != null && doc.getId().startsWith(userPrefix))
                    .limit(TOP_K)
                    .collect(Collectors.toList());
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
            context.append(doc.getContent());
            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * 构建聊天历史
     */
    private String buildChatHistory(List<Message> messages) {
        if (messages.isEmpty()) {
            return "";
        }

        // 只保留最近的几轮对话（避免上下文过长）
        int maxHistory = 10;
        int startIndex = Math.max(0, messages.size() - maxHistory);
        List<Message> recentMessages = messages.subList(startIndex, messages.size());

        StringBuilder history = new StringBuilder();
        for (Message msg : recentMessages) {
            String role = msg.getRole().equals(MessageRole.USER.getCode()) ? "用户" : "助手";
            history.append(String.format("%s: %s\n", role, msg.getContent()));
        }

        return history.toString();
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
