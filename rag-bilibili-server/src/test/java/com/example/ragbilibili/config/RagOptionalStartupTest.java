package com.example.ragbilibili.config;

import com.example.ragbilibili.vectorstore.dashvector.DashVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RagOptionalStartupTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    DashScopeEmbeddingConfig.class,
                    DashVectorConfig.class,
                    SpringAIConfig.class);

    @Test
    void startsWithoutRagCredentialsAndDoesNotCreateRagBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmbeddingModel.class);
            assertThat(context).doesNotHaveBean(DashVectorStore.class);
            assertThat(context).doesNotHaveBean(ChatClient.Builder.class);
        });
    }

    @Test
    void explicitDisableWinsEvenWhenCredentialsExist() {
        contextRunner.withPropertyValues(
                        "rag.enabled=false",
                        "spring.ai.openai.api-key=chat-key",
                        "spring.ai.openai.base-url=https://example.invalid",
                        "spring.ai.dashscope.api-key=embedding-key",
                        "spring.ai.alibaba.dashvector.api-key=vector-key",
                        "spring.ai.alibaba.dashvector.endpoint=example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(DashVectorStore.class);
                    assertThat(context).doesNotHaveBean(ChatClient.Builder.class);
                });
    }

    @Test
    void configuredRagBeansStayLazyUntilFirstRagRequest() {
        contextRunner.withPropertyValues(
                        "spring.ai.openai.api-key=chat-key",
                        "spring.ai.openai.base-url=https://example.invalid",
                        "spring.ai.dashscope.api-key=embedding-key",
                        "spring.ai.alibaba.dashvector.api-key=vector-key",
                        "spring.ai.alibaba.dashvector.endpoint=example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (String beanName : new String[]{
                            "dashscopeEmbeddingModel", "dashVectorClient", "dashVectorCollection",
                            "dashVectorStore", "chatClientBuilder"}) {
                        assertThat(context.getBeanFactory().containsBeanDefinition(beanName)).isTrue();
                        assertThat(context.getBeanFactory().getBeanDefinition(beanName).isLazyInit()).isTrue();
                    }
                    assertThat(context.getBeanFactory().containsBeanDefinition("openAiChatModel")).isTrue();
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
                });
    }

    @Test
    void vectorCapabilityDoesNotRequireChatCredentials() {
        contextRunner.withPropertyValues(
                        "spring.ai.dashscope.api-key=embedding-key",
                        "spring.ai.alibaba.dashvector.api-key=vector-key",
                        "spring.ai.alibaba.dashvector.endpoint=example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanFactory().containsBeanDefinition("dashVectorStore")).isTrue();
                    assertThat(context).doesNotHaveBean(ChatClient.Builder.class);
                });
    }

    @Test
    void chatCapabilityDoesNotRequireVectorCredentials() {
        contextRunner.withPropertyValues(
                        "spring.ai.openai.api-key=chat-key",
                        "spring.ai.openai.base-url=https://example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeanFactory().containsBeanDefinition("chatClientBuilder")).isTrue();
                    assertThat(context).doesNotHaveBean(DashVectorStore.class);
                });
    }
}
