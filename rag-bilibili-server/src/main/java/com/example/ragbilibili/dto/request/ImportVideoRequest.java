package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 导入视频请求
 */
@Data
public class ImportVideoRequest {
    @NotBlank(message = "BV号或URL不能为空")
    private String bvidOrUrl;

    @NotBlank(message = "SESSDATA不能为空")
    private String sessdata;

    @NotBlank(message = "bili_jct不能为空")
    private String biliJct;

    @NotBlank(message = "buvid3不能为空")
    private String buvid3;
}
