package com.example.ragbilibili.service;

import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagDependencyProviderTest {
    @Test
    void reportsStableBusinessErrorWhenRagBeansAreMissing() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DashVectorStore> vectorProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> chatProvider = mock(ObjectProvider.class);
        RagDependencyProvider provider = new RagDependencyProvider(vectorProvider, chatProvider);

        assertThatThrownBy(provider::requireVectorStore)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4003);
        assertThatThrownBy(provider::requireChatClientBuilder)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4003);
    }

    @Test
    void returnsAvailableDependencies() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DashVectorStore> vectorProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> chatProvider = mock(ObjectProvider.class);
        DashVectorStore vectorStore = mock(DashVectorStore.class);
        ChatClient.Builder chatBuilder = mock(ChatClient.Builder.class);
        when(vectorProvider.getIfAvailable()).thenReturn(vectorStore);
        when(chatProvider.getIfAvailable()).thenReturn(chatBuilder);

        RagDependencyProvider provider = new RagDependencyProvider(vectorProvider, chatProvider);

        assertThat(provider.requireVectorStore()).isSameAs(vectorStore);
        assertThat(provider.requireChatClientBuilder()).isSameAs(chatBuilder);
    }
}
