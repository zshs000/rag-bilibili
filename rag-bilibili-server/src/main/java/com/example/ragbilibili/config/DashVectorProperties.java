package com.example.ragbilibili.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.alibaba.dashvector")
public class DashVectorProperties {
    private String apiKey;
    private String endpoint;
    private String collection;
    private Integer defaultTopK = 10;
    private Double similarityThreshold = 0.0;
    private String metric = "cosine";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Integer getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(Integer defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }
}
