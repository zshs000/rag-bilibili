package com.example.ragbilibili.integration.bilibili;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BilibiliWbiSignerTest {
    @Test
    void signsKnownWbiExample() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("foo", 114);
        params.put("bar", 514);
        params.put("zab", 1919810);
        params.put("wts", 1702204169);

        String query = BilibiliWbiSigner.sign(params,
                "7cd084941338484aae1ad9425b84077c",
                "4932caff0ff746eab6f01bf08b70ac45");

        assertEquals("bar=514&foo=114&wts=1702204169&zab=1919810&w_rid=8f6f2b5b3d485fe1886cec6a0be8c5d4", query);
    }

    @Test
    void encodesSpacesAndFiltersForbiddenCharacters() {
        String query = BilibiliWbiSigner.sign(
                Map.of("hello", "世 界!()*", "wts", 1702204169),
                "7cd084941338484aae1ad9425b84077c",
                "4932caff0ff746eab6f01bf08b70ac45");

        org.junit.jupiter.api.Assertions.assertTrue(query.startsWith(
                "hello=%E4%B8%96%20%E7%95%8C&wts=1702204169&"));
    }

    @Test
    void parsesUidAndSpaceUrl() {
        assertEquals(1045711541L, BilibiliUpParser.parseMid("1045711541"));
        assertEquals(1045711541L,
                BilibiliUpParser.parseMid("https://space.bilibili.com/1045711541?spm_id_from=333.1007.0.0"));
    }
}
