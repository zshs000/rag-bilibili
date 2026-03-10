package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会话实体类
 */
@Data
public class Session {
    /**
     * 会话主键
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 会话类型
     */
    private String sessionType;

    /**
     * 关联视频 ID（可为空）
     */
    private Long videoId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
