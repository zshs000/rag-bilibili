package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class CreateSessionRequest {
    @NotBlank(message = "会话类型不能为空")
    private String sessionType;

    /**
     * 视频 ID（单视频对话时必填）
     */
    private Long videoId;
}
