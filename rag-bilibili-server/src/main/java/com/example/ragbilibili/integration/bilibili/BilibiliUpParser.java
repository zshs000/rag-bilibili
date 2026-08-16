package com.example.ragbilibili.integration.bilibili;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliUpParser {
    private static final Pattern MID = Pattern.compile("^(?:https?://space\\.bilibili\\.com/)?(\\d+)(?:[/?#].*)?$");

    private BilibiliUpParser() {
    }

    public static long parseMid(String input) {
        Matcher matcher = MID.matcher(input == null ? "" : input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("请输入 UP 主 UID 或空间链接");
        }
        long mid = Long.parseLong(matcher.group(1));
        if (mid <= 0) {
            throw new IllegalArgumentException("UP 主 UID 必须大于 0");
        }
        return mid;
    }
}
