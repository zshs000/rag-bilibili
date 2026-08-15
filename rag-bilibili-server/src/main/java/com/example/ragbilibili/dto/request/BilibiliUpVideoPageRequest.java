package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BilibiliUpVideoPageRequest {
    @NotBlank(message = "UP主UID或空间链接不能为空")
    private String up;

    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 20, message = "每页最多20个视频")
    private int pageSize = 20;

    private boolean useCredentials;
    private String sessdata;
    private String biliJct;
    private String buvid3;
}
