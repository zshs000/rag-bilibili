package com.example.ragbilibili.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

/**
 * Spring AI 配置
 */
@Configuration
@Conditional(ChatRagConfiguredCondition.class)
@Import({SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
        OpenAiChatAutoConfiguration.class})
public class SpringAIConfig {
    /**
     * 配置 ChatClient.Builder
     */
    @Bean
    @Lazy
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
