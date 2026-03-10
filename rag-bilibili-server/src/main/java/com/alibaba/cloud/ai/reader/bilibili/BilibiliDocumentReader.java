package com.alibaba.cloud.ai.reader.bilibili;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BilibiliDocumentReader implements DocumentReader {
    private static final Logger log = LoggerFactory.getLogger(BilibiliDocumentReader.class);

    private static final String API_VIDEO_INFO = "https://api.bilibili.com/x/web-interface/view";
    private static final String API_PAGE_LIST = "https://api.bilibili.com/x/player/pagelist";
    private static final String API_PLAYER_WBI = "https://api.bilibili.com/x/player/wbi/v2";
    private static final String API_NAV = "https://api.bilibili.com/x/web-interface/nav";
    private static final int[] MIXIN_KEY_ENC_TAB = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52};

    private final BilibiliResource bilibiliResource;
    private final List<BilibiliResource> bilibiliResourceList;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BilibiliDocumentReader(BilibiliResource bilibiliResource) {
        this.bilibiliResource = bilibiliResource;
        this.bilibiliResourceList = null;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public BilibiliDocumentReader(List<BilibiliResource> bilibiliResourceList) {
        this.bilibiliResource = null;
        this.bilibiliResourceList = bilibiliResourceList;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public List<Document> get() {
        List<BilibiliResource> resources = this.bilibiliResourceList;
        if (resources == null) {
            resources = List.of(this.bilibiliResource);
        }

        List<Document> documents = new ArrayList<>();
        for (BilibiliResource resource : resources) {
            documents.addAll(readResource(resource));
        }
        return documents;
    }

    private List<Document> readResource(BilibiliResource resource) {
        try {
            String bvid = resource.getBvid();
            JsonNode videoData = parseJson(sendGet(resource, API_VIDEO_INFO + "?bvid=" + encode(bvid))).path("data");
            String title = videoData.path("title").asText("");
            String description = videoData.path("desc").asText("");

            JsonNode pageData = parseJson(sendGet(resource, API_PAGE_LIST + "?bvid=" + encode(bvid))).path("data");
            if (!pageData.isArray() || pageData.isEmpty()) {
                return List.of();
            }

            StringBuilder allTranscripts = new StringBuilder();
            for (JsonNode page : pageData) {
                long cid = page.path("cid").asLong();
                String transcript = fetchSubtitleTranscript(resource, bvid, cid);
                if (!transcript.isBlank()) {
                    allTranscripts.append(transcript).append(' ');
                }
            }

            String mergedTranscript = allTranscripts.toString().trim();
            if (mergedTranscript.isBlank()) {
                return List.of();
            }

            String content = String.format("Video Title: %s, Description: %s%nTranscript: %s", title, description, mergedTranscript);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("bvid", bvid);
            metadata.put("document_type", "content");
            metadata.put("title", title);
            metadata.put("description", description);
            return List.of(new Document(content, metadata));
        }
        catch (Exception ex) {
            log.error("Failed to read bilibili video: {}", resource.getBvid(), ex);
            throw new RuntimeException("Failed to read bilibili video: " + resource.getBvid(), ex);
        }
    }

    private String fetchSubtitleTranscript(BilibiliResource resource, String bvid, long cid) throws IOException, InterruptedException {
        String mixinKey = getMixinKey(resource);
        Map<String, Object> params = new TreeMap<>();
        params.put("bvid", bvid);
        params.put("cid", cid);
        params.put("wts", System.currentTimeMillis() / 1000);
        params.put("web_location", 1315873);

        String queryString = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));

        String wRid = md5(queryString + mixinKey);
        String playerUrl = API_PLAYER_WBI + "?" + queryString + "&w_rid=" + wRid;
        JsonNode playerData = parseJson(sendGet(resource, playerUrl)).path("data");
        JsonNode subtitleList = playerData.path("subtitle").path("subtitles");

        if (!subtitleList.isArray() || subtitleList.isEmpty()) {
            return "";
        }

        String subtitleUrl = subtitleList.get(0).path("subtitle_url").asText("");
        if (subtitleUrl.startsWith("//")) {
            subtitleUrl = "https:" + subtitleUrl;
        }
        if (subtitleUrl.isBlank()) {
            return "";
        }

        JsonNode subtitleJson = parseJson(sendGet(resource, subtitleUrl));
        StringBuilder transcript = new StringBuilder();
        for (JsonNode node : subtitleJson.path("body")) {
            transcript.append(node.path("content").asText("")).append(' ');
        }
        return transcript.toString().trim();
    }

    private String getMixinKey(BilibiliResource resource) throws IOException, InterruptedException {
        JsonNode navData = parseJson(sendGet(resource, API_NAV)).path("data").path("wbi_img");
        String imgUrl = navData.path("img_url").asText("");
        String subUrl = navData.path("sub_url").asText("");
        String imgKey = extractFileNameWithoutSuffix(imgUrl);
        String subKey = extractFileNameWithoutSuffix(subUrl);
        String rawKey = imgKey + subKey;

        StringBuilder mixinKey = new StringBuilder();
        for (int index : MIXIN_KEY_ENC_TAB) {
            if (index < rawKey.length()) {
                mixinKey.append(rawKey.charAt(index));
            }
        }
        return mixinKey.substring(0, Math.min(32, mixinKey.length()));
    }

    private String sendGet(BilibiliResource resource, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .header("Cookie", buildCookieHeader(resource.getCredentials()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for url: " + url);
        }
        return response.body();
    }

    private String buildCookieHeader(BilibiliCredentials credentials) {
        if (credentials == null) {
            return "";
        }
        List<String> cookies = new ArrayList<>();
        if (hasText(credentials.getSessdata())) {
            cookies.add("SESSDATA=" + credentials.getSessdata());
        }
        if (hasText(credentials.getBiliJct())) {
            cookies.add("bili_jct=" + credentials.getBiliJct());
        }
        if (hasText(credentials.getBuvid3())) {
            cookies.add("buvid3=" + credentials.getBuvid3());
        }
        return String.join("; ", cookies);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String extractFileNameWithoutSuffix(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int dotIndex = fileName.indexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String userAgent() {
        return String.format("SpringAIAlibaba/1.0.0; java/%s; platform/%s; processor/%s", System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String tmp = Integer.toHexString(b & 255);
                if (tmp.length() == 1) {
                    hex.append('0');
                }
                hex.append(tmp);
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 algorithm not found", ex);
        }
    }

    private JsonNode parseJson(String json) throws IOException {
        return objectMapper.readTree(json);
    }
}
