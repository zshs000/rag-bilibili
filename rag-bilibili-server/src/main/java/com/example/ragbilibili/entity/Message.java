package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息实体类
 */
@Data
public class Message {
    /**
     * 消息主键
     */
    private Long id;

    /**
     * 会话 ID
     */
    private Long sessionId;

    /**
     * 角色类型
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
