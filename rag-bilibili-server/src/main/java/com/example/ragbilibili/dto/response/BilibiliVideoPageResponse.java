package com.example.ragbilibili.dto.response;

import java.util.List;

public record BilibiliVideoPageResponse(
        int page,
        int pageSize,
        long total,
        boolean hasMore,
        List<BilibiliSourceVideoResponse> items) {
}
