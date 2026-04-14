package com.example.ragbilibili.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 带视频标题的会话查询结果
 */
@Data
public class SessionWithVideoTitle {
    private Long id;
    private Long userId;
    private String sessionType;
    private Long videoId;
    private LocalDateTime createTime;
    private String videoTitle;
}
