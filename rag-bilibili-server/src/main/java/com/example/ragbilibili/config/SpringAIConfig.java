package com.example.ragbilibili.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置
 */
@Configuration
public class SpringAIConfig {
    /**
     * 配置 TokenTextSplitter
     * V1 版本采用硬编码参数
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
                800,    // defaultChunkSize
                350,    // minChunkSizeChars
                5,      // minChunkLengthToEmbed
                10000,  // maxNumChunks
                true    // keepSeparator
        );
    }
}
