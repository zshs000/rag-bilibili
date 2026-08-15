package com.example.ragbilibili.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoImportItem {
    private Long id;
    private Long batchId;
    private Long userId;
    private String originalInput;
    private String bvid;
    private String status;
    private String failReason;
    private Integer retryCount;
    private Long videoId;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
}
