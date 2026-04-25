package com.example.subtitleeval.reader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliResource {
    private final String bvid;
    private final BilibiliCredentials credentials;

    public BilibiliResource(String bvid, BilibiliCredentials credentials) {
        requireText(bvid, "BV ID must not be empty");
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials must not be null");
        }
        this.bvid = extractBvid(bvid);
        this.credentials = credentials;
    }

    public String getBvid() {
        return bvid;
    }

    public BilibiliCredentials getCredentials() {
        return credentials;
    }

    private String extractBvid(String input) {
        Pattern pattern = Pattern.compile("(BV\\w+)");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Invalid Bilibili video identifier: " + input);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
