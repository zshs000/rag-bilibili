package com.example.ragbilibili.integration.bilibili;

import com.example.ragbilibili.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilibiliApiClientTest {
    private static final BilibiliRequestCredentials CREDENTIALS =
            new BilibiliRequestCredentials("sess", "csrf", "buvid");

    @Test
    void loadsCurrentUsersFoldersAndFavoriteVideos() {
        FakeTransport transport = new FakeTransport();
        BilibiliApiClient client = new BilibiliApiClient(transport, () -> 1684746387L);

        List<BilibiliFavoriteFolder> folders = client.getFavoriteFolders(CREDENTIALS);
        BilibiliSourceVideoPage page = client.getFavoriteVideos(2438609241L, 1, 20, CREDENTIALS);

        assertEquals(1, folders.size());
        assertEquals("默认收藏夹", folders.get(0).title());
        assertEquals(170, folders.get(0).mediaCount());
        assertEquals(2, page.items().size());
        assertFalse(page.items().get(0).unavailable());
        assertTrue(page.items().get(1).unavailable());
        assertTrue(page.hasMore());
        assertTrue(transport.cookies.stream().allMatch(cookie -> cookie.contains("SESSDATA=sess")));
    }

    @Test
    void signsAndLoadsUpVideosWithoutCredentials() {
        FakeTransport transport = new FakeTransport();
        BilibiliApiClient client = new BilibiliApiClient(transport, () -> 1684746387L);

        BilibiliSourceVideoPage page = client.getUpVideos(1045711541L, 1, 20, null);

        assertEquals(2, page.total());
        assertEquals("BV1UCQ3BVESz", page.items().get(0).bvid());
        assertTrue(transport.urls.stream().anyMatch(url -> url.contains("/x/space/wbi/arc/search")
                && url.contains("w_rid=")));
        assertTrue(transport.cookies.stream().allMatch(String::isEmpty));
    }

    @Test
    void mapsLoginAndRiskControlErrors() {
        BilibiliApiClient loginClient = new BilibiliApiClient((url, cookie) ->
                "{\"code\":-101,\"message\":\"账号未登录\"}", () -> 1L);
        BusinessException login = assertThrows(BusinessException.class,
                () -> loginClient.getFavoriteFolders(CREDENTIALS));
        assertEquals(2010, login.getCode());

        BilibiliApiClient riskClient = new BilibiliApiClient((url, cookie) ->
                "{\"code\":-412,\"message\":\"request was banned\"}", () -> 1L);
        BusinessException risk = assertThrows(BusinessException.class,
                () -> riskClient.getUpVideos(1L, 1, 20, null));
        assertEquals(2011, risk.getCode());
    }

    @Test
    void mapsHttp412ToRiskControlError() {
        BilibiliApiClient client = new BilibiliApiClient((url, cookie) -> {
            throw new BilibiliApiClient.HttpStatusException(412);
        }, () -> 1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> client.getUpVideos(1L, 1, 20, null));

        assertEquals(2011, error.getCode());
    }

    @Test
    void treatsMalformedUpVideoDurationAsUnknown() {
        BilibiliApiClient client = new BilibiliApiClient((url, cookie) -> {
            if (url.contains("/x/web-interface/nav")) {
                return """
                        {"code":0,"data":{"wbi_img":{
                          "img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
                          "sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"}}}
                        """;
            }
            return """
                    {"code":0,"data":{"page":{"count":1},"list":{"vlist":[
                      {"bvid":"BV1BAD","length":"--"}
                    ]}}}
                    """;
        }, () -> 1L);

        BilibiliSourceVideoPage page = client.getUpVideos(1L, 1, 20, null);

        assertEquals(0, page.items().get(0).durationSeconds());
    }

    private static final class FakeTransport implements BilibiliApiClient.HttpTransport {
        private final List<String> urls = new ArrayList<>();
        private final List<String> cookies = new ArrayList<>();

        @Override
        public String get(String url, String cookie) throws IOException {
            urls.add(url);
            cookies.add(cookie);
            if (url.contains("/x/web-interface/nav")) {
                return """
                        {"code":0,"data":{"isLogin":true,"mid":1045711541,"wbi_img":{
                          "img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
                          "sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"}}}
                        """;
            }
            if (url.contains("/folder/created/list-all")) {
                return """
                        {"code":0,"data":{"count":1,"list":[
                          {"id":2438609241,"title":"默认收藏夹","media_count":170,"attr":0}
                        ]}}
                        """;
            }
            if (url.contains("/fav/resource/list")) {
                return """
                        {"code":0,"data":{"has_more":true,"info":{"media_count":170},"medias":[
                          {"bvid":"BV1GOOD","title":"正常视频","cover":"https://i0/cover.jpg","duration":131,
                           "upper":{"mid":1,"name":"UP甲"},"pubtime":1755067636,"attr":0},
                          {"bvid":"BV1GONE","title":"已失效视频","cover":"https://i0/gone.jpg","duration":10,
                           "upper":{"mid":2,"name":"UP乙"},"pubtime":1755067637,"attr":9}
                        ]}}
                        """;
            }
            if (url.contains("/x/space/wbi/arc/search")) {
                return """
                        {"code":0,"data":{"page":{"count":2,"pn":1,"ps":20},"list":{"vlist":[
                          {"bvid":"BV1UCQ3BVESz","title":"问卷星自动化","pic":"https://i0/up.jpg","length":"01:44",
                           "mid":1045711541,"author":"测试UP","created":1755067636}
                        ]}}}
                        """;
            }
            throw new IOException("unexpected url");
        }
    }
}
