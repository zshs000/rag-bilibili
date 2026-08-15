package com.example.ragbilibili.dto.response;

public record BilibiliSourceVideoResponse(
        String bvid,
        String title,
        String coverUrl,
        long durationSeconds,
        long ownerMid,
        String ownerName,
        long publishTime,
        boolean unavailable) {
}
