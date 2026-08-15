package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateVideoImportBatchRequest {
    @NotEmpty(message = "至少需要一个视频")
    private List<String> inputs;

    @NotBlank(message = "SESSDATA不能为空")
    private String sessdata;

    @NotBlank(message = "bili_jct不能为空")
    private String biliJct;

    @NotBlank(message = "buvid3不能为空")
    private String buvid3;
}
