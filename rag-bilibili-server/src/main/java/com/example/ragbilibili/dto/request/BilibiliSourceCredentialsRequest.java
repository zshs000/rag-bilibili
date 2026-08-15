package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BilibiliSourceCredentialsRequest {
    @NotBlank(message = "SESSDATA不能为空")
    private String sessdata;

    @NotBlank(message = "bili_jct不能为空")
    private String biliJct;

    @NotBlank(message = "buvid3不能为空")
    private String buvid3;
}
