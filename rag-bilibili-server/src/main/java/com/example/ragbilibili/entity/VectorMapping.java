package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 向量映射实体类
 */
@Data
public class VectorMapping {
    /**
     * 映射主键
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 视频 ID
     */
    private Long videoId;

    /**
     * 分片 ID
     */
    private Long chunkId;

    /**
     * 向量 ID
     */
    private String vectorId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
