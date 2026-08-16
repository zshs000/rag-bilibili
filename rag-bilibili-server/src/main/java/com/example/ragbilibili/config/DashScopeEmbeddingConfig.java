package com.example.ragbilibili.config;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingProperties;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(VectorRagConfiguredCondition.class)
@EnableConfigurationProperties({DashScopeConnectionProperties.class, DashScopeEmbeddingProperties.class})
public class DashScopeEmbeddingConfig {

    @Bean("dashscopeEmbeddingModel")
    @Primary
    @Lazy
    public EmbeddingModel dashscopeEmbeddingModel(DashScopeConnectionProperties connectionProperties,
                                                  DashScopeEmbeddingProperties embeddingProperties) {
        String apiKey = StringUtils.hasText(embeddingProperties.getApiKey())
                ? embeddingProperties.getApiKey()
                : connectionProperties.getApiKey();
        Assert.hasText(apiKey, "DashScope API key must not be empty");

        DashScopeApi.Builder apiBuilder = DashScopeApi.builder().apiKey(apiKey);
        if (StringUtils.hasText(embeddingProperties.getBaseUrl())) {
            apiBuilder.baseUrl(embeddingProperties.getBaseUrl());
        } else if (StringUtils.hasText(connectionProperties.getBaseUrl())) {
            apiBuilder.baseUrl(connectionProperties.getBaseUrl());
        }
        if (StringUtils.hasText(embeddingProperties.getWorkspaceId())) {
            apiBuilder.workSpaceId(embeddingProperties.getWorkspaceId());
        } else if (StringUtils.hasText(connectionProperties.getWorkspaceId())) {
            apiBuilder.workSpaceId(connectionProperties.getWorkspaceId());
        }

        DashScopeEmbeddingOptions options = embeddingProperties.getOptions();
        if (options == null) {
            options = new DashScopeEmbeddingOptions();
        }
        if (!StringUtils.hasText(options.getModel())) {
            options.setModel(DashScopeApi.DEFAULT_EMBEDDING_MODEL);
        }
        if (!StringUtils.hasText(options.getTextType())) {
            options.setTextType(DashScopeApi.DEFAULT_EMBEDDING_TEXT_TYPE);
        }

        MetadataMode metadataMode = embeddingProperties.getMetadataMode() == null
                ? MetadataMode.EMBED
                : embeddingProperties.getMetadataMode();

        return new DashScopeEmbeddingModel(apiBuilder.build(), metadataMode, options);
    }
}
