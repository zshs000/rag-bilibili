package com.example.ragbilibili.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BilibiliFavoritePageRequest extends BilibiliSourceCredentialsRequest {
    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 20, message = "每页最多20个视频")
    private int pageSize = 20;
}
