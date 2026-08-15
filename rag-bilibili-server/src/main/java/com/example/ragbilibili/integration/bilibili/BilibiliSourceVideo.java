package com.example.ragbilibili.integration.bilibili;

public record BilibiliSourceVideo(
        String bvid,
        String title,
        String coverUrl,
        long durationSeconds,
        long ownerMid,
        String ownerName,
        long publishTime,
        boolean unavailable) {
}
