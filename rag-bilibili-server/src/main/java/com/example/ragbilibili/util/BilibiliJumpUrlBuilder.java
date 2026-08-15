package com.example.ragbilibili.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BilibiliJumpUrlBuilder {
    private static final long PREROLL_MS = 2500L;

    public String build(String bvid, Integer pageNumber, Long startTimeMs) {
        String normalizedBvid = BVIDParser.parse(bvid);
        int normalizedPage = pageNumber == null || pageNumber < 1 ? 1 : pageNumber;
        long jumpTimeMs = Math.max(0L, (startTimeMs == null ? 0L : startTimeMs) - PREROLL_MS);
        String seconds = BigDecimal.valueOf(jumpTimeMs)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString();
        return "https://www.bilibili.com/video/" + normalizedBvid
                + "/?p=" + normalizedPage + "&t=" + seconds;
    }
}
