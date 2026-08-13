package com.alibaba.cloud.ai.reader.bilibili;

import java.util.List;

/**
 * Structured subtitles and metadata for one requested Bilibili resource.
 */
public record BilibiliVideoSubtitles(
        String bvid,
        String title,
        String description,
        List<BilibiliSubtitlePage> pages
) {
    public BilibiliVideoSubtitles {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
