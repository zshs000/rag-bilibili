package com.example.ragbilibili.transformer;

public record TimestampedSubtitleChunk(
        long cid,
        int pageNumber,
        String subtitleLanguage,
        long startTimeMs,
        long endTimeMs,
        String text
) {
}
