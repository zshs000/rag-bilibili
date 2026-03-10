package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 视频实体类
 */
@Data
public class Video {
    /**
     * 视频主键
     */
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * BV 号
     */
    private String bvid;

    /**
     * 视频标题
     */
    private String title;

    /**
     * 视频简介
     */
    private String description;

    /**
     * 导入时间
     */
    private LocalDateTime importTime;

    /**
     * 状态
     */
    private String status;

    /**
     * 导入失败原因
     */
    private String failReason;
}
