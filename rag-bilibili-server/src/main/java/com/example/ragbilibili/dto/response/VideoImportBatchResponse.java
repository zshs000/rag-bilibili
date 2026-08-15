package com.example.ragbilibili.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VideoImportBatchResponse {
    private Long id;
    private String status;
    private Integer totalCount;
    private Integer queuedCount;
    private Integer runningCount;
    private Integer succeededCount;
    private Integer skippedCount;
    private Integer failedCount;
    private String createTime;
    private String updateTime;
    private String finishTime;
    private List<VideoImportItemResponse> items = new ArrayList<>();
}
