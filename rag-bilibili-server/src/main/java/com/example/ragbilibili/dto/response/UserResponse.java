package com.example.ragbilibili.dto.response;

import lombok.Data;

/**
 * 用户响应
 */
@Data
public class UserResponse {
    private Long id;
    private String username;
    private String createTime;
}
