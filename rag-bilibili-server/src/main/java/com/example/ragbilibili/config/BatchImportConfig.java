package com.example.ragbilibili.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BatchImportProperties.class)
public class BatchImportConfig {
}
