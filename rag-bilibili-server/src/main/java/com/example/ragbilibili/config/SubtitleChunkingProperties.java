package com.example.ragbilibili.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.subtitle-chunking")
public class SubtitleChunkingProperties {
    private int targetTokens = 400;
    private int minTokens = 300;
    private int maxTokens = 500;
    private int overlapCues = 2;

    public int getTargetTokens() {
        return targetTokens;
    }

    public void setTargetTokens(int targetTokens) {
        this.targetTokens = targetTokens;
    }

    public int getMinTokens() {
        return minTokens;
    }

    public void setMinTokens(int minTokens) {
        this.minTokens = minTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getOverlapCues() {
        return overlapCues;
    }

    public void setOverlapCues(int overlapCues) {
        this.overlapCues = overlapCues;
    }
}
