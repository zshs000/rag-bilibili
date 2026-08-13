package com.alibaba.cloud.ai.reader.bilibili;

import java.math.BigDecimal;

/**
 * One subtitle cue exactly as exposed by Bilibili's subtitle document.
 */
public record BilibiliSubtitleCue(
        BigDecimal from,
        BigDecimal to,
        Long sid,
        Integer location,
        String content
) {
}
