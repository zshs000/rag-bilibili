package com.example.ragbilibili.dto.response;

import lombok.Data;

/**
 * 视频响应
 */
@Data
public class VideoResponse {
    private Long id;
    private String bvid;
    private String title;
    private String description;
    private Integer chunkCount;
    private String importTime;
    private String status;
    private String failReason;
}
