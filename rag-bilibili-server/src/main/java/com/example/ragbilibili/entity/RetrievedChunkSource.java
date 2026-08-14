package com.example.ragbilibili.entity;

import lombok.Data;

/**
 * 向量检索结果对应的分片来源投影。
 */
@Data
public class RetrievedChunkSource {
    private String vectorId;
    private String bvid;
    private String videoTitle;
    private Long cid;
    private Integer pageNumber;
    private Long startTimeMs;
    private Long endTimeMs;
    private String snippet;
}
