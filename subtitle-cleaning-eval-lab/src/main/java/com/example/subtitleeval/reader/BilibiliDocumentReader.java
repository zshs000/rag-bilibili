package com.example.subtitleeval.reader;

import com.example.subtitleeval.model.EvalDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BilibiliDocumentReader {
    private static final String API_VIDEO_INFO = "https://api.bilibili.com/x/web-interface/view";
    private static final String API_PAGE_LIST = "https://api.bilibili.com/x/player/pagelist";
    private static final String API_PLAYER_WBI = "https://api.bilibili.com/x/player/wbi/v2";
    private static final String API_NAV = "https://api.bilibili.com/x/web-interface/nav";
    private static final int[] MIXIN_KEY_ENC_TAB = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52};

    private final BilibiliResource bilibiliResource;
    private final List<BilibiliResource> bilibiliResourceList;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean debugEnabled;
    private final Path dumpDir;

    public BilibiliDocumentReader(BilibiliResource bilibiliResource) {
        this.bilibiliResource = bilibiliResource;
        this.bilibiliResourceList = null;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.debugEnabled = isDebugEnabled();
        this.dumpDir = resolveDumpDir();
    }

    public BilibiliDocumentReader(List<BilibiliResource> bilibiliResourceList) {
        this.bilibiliResource = null;
        this.bilibiliResourceList = bilibiliResourceList;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.debugEnabled = isDebugEnabled();
        this.dumpDir = resolveDumpDir();
    }

    public List<EvalDocument> get() {
        List<BilibiliResource> resources = this.bilibiliResourceList;
        if (resources == null) {
            resources = List.of(this.bilibiliResource);
        }

        List<EvalDocument> documents = new ArrayList<>();
        for (BilibiliResource resource : resources) {
            documents.addAll(readResource(resource));
        }
        return documents;
    }

    private List<EvalDocument> readResource(BilibiliResource resource) {
        try {
            String bvid = resource.getBvid();
            debug("=== start readResource bvid=%s ===", bvid);
            JsonNode videoData = parseJson(sendGet(resource, API_VIDEO_INFO + "?bvid=" + encode(bvid))).path("data");
            String title = videoData.path("title").asText("");
            String description = videoData.path("desc").asText("");
            debug("video title=%s", title);

            JsonNode pageData = parseJson(sendGet(resource, API_PAGE_LIST + "?bvid=" + encode(bvid))).path("data");
            if (!pageData.isArray() || pageData.isEmpty()) {
                debug("pagelist empty for bvid=%s", bvid);
                dumpJson("pagelist-" + bvid + ".json", pageData);
                return List.of();
            }
            debug("pagelist size=%s", pageData.size());

            StringBuilder allTranscripts = new StringBuilder();
            for (JsonNode page : pageData) {
                long cid = page.path("cid").asLong();
                debug("read page=%s cid=%s part=%s", page.path("page").asText(""), cid, page.path("part").asText(""));
                String transcript = fetchSubtitleTranscript(resource, bvid, cid);
                debug("cid=%s transcript length=%s", cid, transcript.length());
                if (!transcript.isBlank()) {
                    if (!allTranscripts.isEmpty()) {
                        allTranscripts.append('\n');
                    }
                    allTranscripts.append(transcript);
                }
            }

            String mergedTranscript = allTranscripts.toString().trim();
            if (mergedTranscript.isBlank()) {
                debug("merged transcript blank for bvid=%s", bvid);
                return List.of();
            }
            debug("merged transcript final length=%s", mergedTranscript.length());

            String content = String.format("Video Title: %s, Description: %s%nTranscript: %s", title, description, mergedTranscript);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("bvid", bvid);
            metadata.put("document_type", "content");
            metadata.put("title", title);
            metadata.put("description", description);
            return List.of(EvalDocument.builder().text(content).metadata(metadata).build());
        } catch (Exception ex) {
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
        debug("player url for cid=%s -> %s", cid, playerUrl);
        JsonNode playerResponse = parseJson(sendGet(resource, playerUrl));
        debug("player response code for cid=%s -> %s", cid, playerResponse.path("code").asText(""));
        debug("player response message for cid=%s -> %s", cid, playerResponse.path("message").asText(""));
        JsonNode playerData = playerResponse.path("data");
        JsonNode subtitleNode = playerData.path("subtitle");
        JsonNode subtitleList = subtitleNode.path("subtitles");
        debug("subtitle count for cid=%s -> %s", cid, subtitleList.isArray() ? subtitleList.size() : -1);

        if (!subtitleList.isArray() || subtitleList.isEmpty()) {
            dumpText("player-url-" + bvid + "-" + cid + ".txt", playerUrl);
            dumpJson("player-response-" + bvid + "-" + cid + ".json", playerResponse);
            dumpJson("subtitle-node-" + bvid + "-" + cid + ".json", subtitleNode);
            debug("subtitle node raw for cid=%s -> %s", cid, subtitleNode.isMissingNode() ? "<missing>" : subtitleNode.toString());
            debug("subtitle list empty for cid=%s", cid);
            return "";
        }

        String subtitleUrl = subtitleList.get(0).path("subtitle_url").asText("");
        if (subtitleUrl.startsWith("//")) {
            subtitleUrl = "https:" + subtitleUrl;
        }
        if (subtitleUrl.isBlank()) {
            debug("subtitle url blank for cid=%s", cid);
            return "";
        }
        debug("subtitle url for cid=%s -> %s", cid, subtitleUrl);

        JsonNode subtitleJson = parseJson(sendGet(resource, subtitleUrl));
        StringBuilder transcript = new StringBuilder();
        for (JsonNode node : subtitleJson.path("body")) {
            String segment = node.path("content").asText("").trim();
            if (segment.isBlank()) {
                continue;
            }
            if (!transcript.isEmpty()) {
                transcript.append('\n');
            }
            transcript.append(segment);
        }
        debug("subtitle body count for cid=%s -> %s", cid, subtitleJson.path("body").isArray() ? subtitleJson.path("body").size() : -1);
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
        return String.format("SubtitleCleaningEvalLab/1.0.0; java/%s; platform/%s; processor/%s",
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"));
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
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 algorithm not found", ex);
        }
    }

    private JsonNode parseJson(String json) throws IOException {
        return objectMapper.readTree(json);
    }

    private boolean isDebugEnabled() {
        return Boolean.parseBoolean(System.getProperty("subtitleeval.reader.debug", "false"))
                || "true".equalsIgnoreCase(System.getenv("SUBTITLEEVAL_READER_DEBUG"));
    }

    private Path resolveDumpDir() {
        String fromProperty = System.getProperty("subtitleeval.reader.dumpDir", "").trim();
        if (!fromProperty.isBlank()) {
            return Paths.get(fromProperty);
        }
        String fromEnv = System.getenv("SUBTITLEEVAL_READER_DUMP_DIR");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv.trim());
        }
        return null;
    }

    private void dumpJson(String fileName, JsonNode jsonNode) {
        if (dumpDir == null || jsonNode == null || jsonNode.isMissingNode()) {
            return;
        }
        try {
            Files.createDirectories(dumpDir);
            Path target = dumpDir.resolve(fileName);
            Files.writeString(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode), StandardCharsets.UTF_8);
            debug("dump json -> %s", target.toAbsolutePath());
        } catch (Exception ex) {
            debug("dump json failed: %s", ex.getMessage());
        }
    }

    private void dumpText(String fileName, String content) {
        if (dumpDir == null || content == null || content.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(dumpDir);
            Path target = dumpDir.resolve(fileName);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            debug("dump text -> %s", target.toAbsolutePath());
        } catch (Exception ex) {
            debug("dump text failed: %s", ex.getMessage());
        }
    }

    private void debug(String format, Object... args) {
        if (!debugEnabled) {
            return;
        }
        System.out.println("[reader-debug] " + String.format(format, args));
    }
}
