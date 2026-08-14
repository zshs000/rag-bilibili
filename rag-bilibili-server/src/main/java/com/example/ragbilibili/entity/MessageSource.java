package com.example.ragbilibili.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageSource {
    private Long id;
    private Long messageId;
    private Integer citationIndex;
    private String vectorId;
    private String bvid;
    private String videoTitle;
    private Long cid;
    private Integer pageNumber;
    private Long startTimeMs;
    private Long endTimeMs;
    private String snippet;
    private LocalDateTime createTime;
}
