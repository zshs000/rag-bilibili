package com.example.ragbilibili.integration.bilibili;

import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Component
public class BilibiliApiClient {
    private static final String API_NAV = "https://api.bilibili.com/x/web-interface/nav";
    private static final String API_FOLDERS = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=";
    private static final String API_FAVORITES = "https://api.bilibili.com/x/v3/fav/resource/list";
    private static final String API_UP_VIDEOS = "https://api.bilibili.com/x/space/wbi/arc/search";

    private final HttpTransport transport;
    private final LongSupplier epochSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BilibiliApiClient() {
        this(new JdkHttpTransport(), () -> System.currentTimeMillis() / 1000);
    }

    BilibiliApiClient(HttpTransport transport, LongSupplier epochSeconds) {
        this.transport = transport;
        this.epochSeconds = epochSeconds;
    }

    public List<BilibiliFavoriteFolder> getFavoriteFolders(BilibiliRequestCredentials credentials) {
        JsonNode nav = request(API_NAV, credentials, false).path("data");
        long mid = nav.path("mid").asLong();
        if (!nav.path("isLogin").asBoolean(false) || mid <= 0) {
            throw new BusinessException(ErrorCode.BILIBILI_CREDENTIAL_INVALID);
        }
        JsonNode list = request(API_FOLDERS + mid, credentials, false).path("data").path("list");
        List<BilibiliFavoriteFolder> folders = new ArrayList<>();
        for (JsonNode node : list) {
            folders.add(new BilibiliFavoriteFolder(
                    node.path("id").asLong(),
                    node.path("title").asText(""),
                    node.path("media_count").asInt(),
                    (node.path("attr").asInt() & 2) != 0));
        }
        return List.copyOf(folders);
    }

    public BilibiliSourceVideoPage getFavoriteVideos(long folderId, int page, int pageSize,
                                                       BilibiliRequestCredentials credentials) {
        String url = API_FAVORITES + "?media_id=" + folderId + "&pn=" + page + "&ps=" + pageSize
                + "&platform=web";
        JsonNode data = request(url, credentials, false).path("data");
        List<BilibiliSourceVideo> items = new ArrayList<>();
        for (JsonNode node : data.path("medias")) {
            int attr = node.path("attr").asInt();
            String bvid = node.path("bvid").asText("");
            items.add(new BilibiliSourceVideo(
                    bvid,
                    node.path("title").asText(""),
                    normalizeImage(node.path("cover").asText("")),
                    node.path("duration").asLong(),
                    node.path("upper").path("mid").asLong(),
                    node.path("upper").path("name").asText(""),
                    node.path("pubtime").asLong(),
                    attr == 9 || bvid.isBlank()));
        }
        long total = data.path("info").path("media_count").asLong(items.size());
        return new BilibiliSourceVideoPage(page, pageSize, total,
                data.path("has_more").asBoolean(false), List.copyOf(items));
    }

    public BilibiliSourceVideoPage getUpVideos(long mid, int page, int pageSize,
                                                BilibiliRequestCredentials credentials) {
        JsonNode nav = request(API_NAV, credentials, credentials == null).path("data").path("wbi_img");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mid", mid);
        params.put("order", "pubdate");
        params.put("pn", page);
        params.put("ps", pageSize);
        params.put("wts", epochSeconds.getAsLong());
        String query = BilibiliWbiSigner.sign(params,
                fileKey(nav.path("img_url").asText("")), fileKey(nav.path("sub_url").asText("")));
        JsonNode data = request(API_UP_VIDEOS + "?" + query, credentials, false).path("data");
        List<BilibiliSourceVideo> items = new ArrayList<>();
        for (JsonNode node : data.path("list").path("vlist")) {
            String bvid = node.path("bvid").asText("");
            items.add(new BilibiliSourceVideo(
                    bvid,
                    node.path("title").asText(""),
                    normalizeImage(node.path("pic").asText("")),
                    parseDuration(node.path("length").asText("0")),
                    node.path("mid").asLong(mid),
                    node.path("author").asText(""),
                    node.path("created").asLong(),
                    bvid.isBlank()));
        }
        JsonNode pageNode = data.path("page");
        long total = pageNode.path("count").asLong(items.size());
        return new BilibiliSourceVideoPage(page, pageSize, total,
                (long) page * pageSize < total, List.copyOf(items));
    }

    private JsonNode request(String url, BilibiliRequestCredentials credentials, boolean allowAnonymousNav) {
        try {
            JsonNode root = objectMapper.readTree(transport.get(url, cookieHeader(credentials)));
            int code = root.path("code").asInt(Integer.MIN_VALUE);
            if (code == 0 || allowAnonymousNav && code == -101 && root.path("data").has("wbi_img")) {
                return root;
            }
            if (code == -101) {
                throw new BusinessException(ErrorCode.BILIBILI_CREDENTIAL_INVALID);
            }
            if (code == -412) {
                throw new BusinessException(ErrorCode.BILIBILI_RISK_CONTROLLED);
            }
            throw new BusinessException(ErrorCode.BILIBILI_SOURCE_REQUEST_FAILED);
        }
        catch (BusinessException ex) {
            throw ex;
        }
        catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.BILIBILI_SOURCE_REQUEST_FAILED);
        }
    }

    private static String cookieHeader(BilibiliRequestCredentials credentials) {
        if (credentials == null) {
            return "";
        }
        return "SESSDATA=" + credentials.sessdata() + "; bili_jct=" + credentials.biliJct()
                + "; buvid3=" + credentials.buvid3();
    }

    private static String fileKey(String url) {
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        return slash >= 0 && dot > slash ? url.substring(slash + 1, dot) : "";
    }

    private static String normalizeImage(String url) {
        return url.startsWith("//") ? "https:" + url : url;
    }

    private static long parseDuration(String value) {
        String[] parts = value.split(":");
        long result = 0;
        for (String part : parts) {
            result = result * 60 + Long.parseLong(part);
        }
        return result;
    }

    @FunctionalInterface
    interface HttpTransport {
        String get(String url, String cookie) throws IOException, InterruptedException;
    }

    private static final class JdkHttpTransport implements HttpTransport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        @Override
        public String get(String url, String cookie) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com/")
                    .GET();
            if (!cookie.isBlank()) {
                builder.header("Cookie", cookie);
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Bilibili HTTP status " + response.statusCode());
            }
            return response.body();
        }
    }
}
