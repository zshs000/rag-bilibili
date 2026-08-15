package com.example.ragbilibili.dto.response;

import lombok.Data;

@Data
public class VideoImportItemResponse {
    private Long id;
    private String originalInput;
    private String bvid;
    private String status;
    private String failReason;
    private Integer retryCount;
    private Long videoId;
    private String createTime;
    private String startTime;
    private String finishTime;
}
