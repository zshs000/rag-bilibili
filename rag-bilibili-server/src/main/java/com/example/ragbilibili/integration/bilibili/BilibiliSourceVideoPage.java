package com.example.ragbilibili.integration.bilibili;

import java.util.List;

public record BilibiliSourceVideoPage(
        int page,
        int pageSize,
        long total,
        boolean hasMore,
        List<BilibiliSourceVideo> items) {
}
