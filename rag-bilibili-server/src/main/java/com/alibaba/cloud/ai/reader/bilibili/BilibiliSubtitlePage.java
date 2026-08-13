package com.alibaba.cloud.ai.reader.bilibili;

import java.util.List;

/**
 * One page (part) of a Bilibili video and its subtitle tracks.
 */
public record BilibiliSubtitlePage(
        long cid,
        int page,
        String part,
        List<BilibiliSubtitleTrack> tracks
) {
    public BilibiliSubtitlePage {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
