package com.example.ragbilibili.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoImportBatch {
    private Long id;
    private Long userId;
    private String status;
    private Integer totalCount;
    private Integer queuedCount;
    private Integer runningCount;
    private Integer succeededCount;
    private Integer skippedCount;
    private Integer failedCount;
    private String credentialsCiphertext;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;
}
