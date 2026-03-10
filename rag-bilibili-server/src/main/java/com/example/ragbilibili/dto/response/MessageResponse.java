package com.example.ragbilibili.dto.response;

import lombok.Data;

/**
 * 消息响应
 */
@Data
public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private String createTime;
}
