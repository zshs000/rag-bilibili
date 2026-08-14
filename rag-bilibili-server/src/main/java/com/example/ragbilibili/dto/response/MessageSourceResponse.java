package com.example.ragbilibili.dto.response;

import lombok.Data;

@Data
public class MessageSourceResponse {
    private Integer index;
    private String bvid;
    private String videoTitle;
    private Integer pageNumber;
    private Long startTimeMs;
    private Long endTimeMs;
    private String snippet;
    private String jumpUrl;
}
