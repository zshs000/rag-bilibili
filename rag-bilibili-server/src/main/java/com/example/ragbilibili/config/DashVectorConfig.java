package com.example.ragbilibili.config;

import com.alibaba.cloud.ai.vectorstore.dashvector.DashVectorStore;
import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorCollection;
import com.aliyun.dashvector.common.DashVectorException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@EnableConfigurationProperties(DashVectorProperties.class)
public class DashVectorConfig {
    @Bean
    public DashVectorClient dashVectorClient(DashVectorProperties properties) throws DashVectorException {
        Assert.hasText(properties.getApiKey(), "DashVector API key must not be empty");
        Assert.hasText(properties.getEndpoint(), "DashVector endpoint must not be empty");
        return new DashVectorClient(properties.getApiKey(), properties.getEndpoint());
    }

    @Bean
    public DashVectorCollection dashVectorCollection(DashVectorClient client, DashVectorProperties properties) {
        Assert.hasText(properties.getCollection(), "DashVector collection must not be empty");
        return client.get(properties.getCollection());
    }

    @Bean
    public DashVectorStore dashVectorStore(DashVectorCollection collection, EmbeddingModel embeddingModel,
                                           DashVectorProperties properties) {
        return new DashVectorStore(
                collection,
                properties.getCollection(),
                embeddingModel,
                properties.getDefaultTopK() == null ? 10 : properties.getDefaultTopK(),
                properties.getSimilarityThreshold() == null ? 0.0 : properties.getSimilarityThreshold(),
                properties.getMetric()
        );
    }
}
