package com.example.ragbilibili.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.List;

/** 对话能力只依赖 OpenAI 兼容接口配置。 */
public class ChatRagConfiguredCondition implements Condition {
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.ai.openai.api-key",
            "spring.ai.openai.base-url"
    );

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!context.getEnvironment().getProperty("rag.enabled", Boolean.class, true)) {
            return false;
        }
        return REQUIRED_PROPERTIES.stream()
                .allMatch(name -> StringUtils.hasText(context.getEnvironment().getProperty(name)));
    }
}
