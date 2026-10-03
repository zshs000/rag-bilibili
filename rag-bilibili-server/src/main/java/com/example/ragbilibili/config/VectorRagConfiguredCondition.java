package com.example.ragbilibili.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.List;

/** 向量检索能力需要 Embedding 与 DashVector 配置同时完整。 */
public class VectorRagConfiguredCondition implements Condition {
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.ai.dashscope.api-key",
            "spring.ai.alibaba.dashvector.api-key",
            "spring.ai.alibaba.dashvector.endpoint"
    );

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ragEnabled(context) && REQUIRED_PROPERTIES.stream()
                .allMatch(name -> StringUtils.hasText(context.getEnvironment().getProperty(name)));
    }

    private boolean ragEnabled(ConditionContext context) {
        return context.getEnvironment().getProperty("rag.enabled", Boolean.class, true);
    }
}
