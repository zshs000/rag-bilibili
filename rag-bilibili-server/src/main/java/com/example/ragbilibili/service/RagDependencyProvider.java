package com.example.ragbilibili.service;

import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 隔离可选 RAG 基础设施，避免普通业务接口被 AI/向量服务启动状态绑定。
 */
@Component
public class RagDependencyProvider {
    private static final Logger log = LoggerFactory.getLogger(RagDependencyProvider.class);

    private final ObjectProvider<DashVectorStore> vectorStoreProvider;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public RagDependencyProvider(ObjectProvider<DashVectorStore> vectorStoreProvider,
                                 ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public DashVectorStore requireVectorStore() {
        return require(vectorStoreProvider, "DashVector");
    }

    public ChatClient.Builder requireChatClientBuilder() {
        return require(chatClientBuilderProvider, "ChatModel");
    }

    public DashVectorStore vectorStoreIfAvailable() {
        try {
            return vectorStoreProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("DashVector 当前不可用，跳过非关键向量操作", e);
            return null;
        }
    }

    private <T> T require(ObjectProvider<T> provider, String dependencyName) {
        try {
            T dependency = provider.getIfAvailable();
            if (dependency != null) {
                return dependency;
            }
        } catch (RuntimeException e) {
            log.warn("RAG 依赖初始化失败: dependency={}", dependencyName, e);
        }
        throw new BusinessException(ErrorCode.RAG_UNAVAILABLE);
    }
}
