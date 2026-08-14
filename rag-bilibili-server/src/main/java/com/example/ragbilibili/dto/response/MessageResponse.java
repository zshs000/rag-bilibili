package com.example.ragbilibili.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息响应
 */
@Data
public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private String createTime;
    private List<MessageSourceResponse> sources = new ArrayList<>();
}
