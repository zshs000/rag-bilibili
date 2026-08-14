package com.example.ragbilibili.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BilibiliJumpUrlBuilderTest {
    private final BilibiliJumpUrlBuilder builder = new BilibiliJumpUrlBuilder();

    @Test
    void shouldApplyTwoPointFiveSecondPrerollAndKeepFractionalSeconds() {
        assertEquals(
                "https://www.bilibili.com/video/BV1iH3763Ezm/?p=1&t=127.5",
                builder.build("BV1iH3763Ezm", 1, 130_000L));
    }

    @Test
    void shouldClampTimeAtZeroAndDefaultPageToOne() {
        assertEquals(
                "https://www.bilibili.com/video/BV1iH3763Ezm/?p=1&t=0",
                builder.build("BV1iH3763Ezm", null, 1_000L));
    }
}
