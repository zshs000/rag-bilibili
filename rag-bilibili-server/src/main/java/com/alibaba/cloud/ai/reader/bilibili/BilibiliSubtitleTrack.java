package com.alibaba.cloud.ai.reader.bilibili;

import java.util.List;

/**
 * A language track within one Bilibili video page.
 */
public record BilibiliSubtitleTrack(
        Long id,
        String language,
        String languageDescription,
        boolean locked,
        List<BilibiliSubtitleCue> cues
) {
    public BilibiliSubtitleTrack {
        cues = cues == null ? List.of() : List.copyOf(cues);
    }
}
