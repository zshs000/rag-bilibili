package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分片实体类
 */
@Data
public class Chunk {
    /**
     * 分片主键
     */
    private Long id;

    /**
     * 视频 ID
     */
    private Long videoId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * BV 号（冗余存储）
     */
    private String bvid;

    /**
     * 视频标题（冗余存储）
     */
    private String title;

    /**
     * 分片序号
     */
    private Integer chunkIndex;

    /**
     * 总分片数（冗余存储）
     */
    private Integer totalChunks;

    /**
     * 分片文本
     */
    private String chunkText;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
