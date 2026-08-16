package com.example.ragbilibili.integration.bilibili;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class BilibiliWbiSigner {
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private BilibiliWbiSigner() {
    }

    public static String sign(Map<String, Object> params, String imgKey, String subKey) {
        String query = new TreeMap<>(params).entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "="
                        + encode(String.valueOf(entry.getValue()).replaceAll("[!'()*]", "")))
                .collect(Collectors.joining("&"));
        return query + "&w_rid=" + md5(query + mixinKey(imgKey + subKey));
    }

    private static String mixinKey(String rawKey) {
        StringBuilder mixed = new StringBuilder();
        for (int index : MIXIN_KEY_ENC_TAB) {
            if (index < rawKey.length()) {
                mixed.append(rawKey.charAt(index));
            }
        }
        return mixed.substring(0, Math.min(32, mixed.length()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 algorithm not found", ex);
        }
    }
}
