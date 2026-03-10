package com.example.ragbilibili.dto.response;

import lombok.Data;

/**
 * 会话响应
 */
@Data
public class SessionResponse {
    private Long id;
    private String sessionType;
    private Long videoId;
    private String videoTitle;
    private String createTime;
}
