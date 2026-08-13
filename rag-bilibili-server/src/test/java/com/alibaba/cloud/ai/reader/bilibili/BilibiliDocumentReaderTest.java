package com.alibaba.cloud.ai.reader.bilibili;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BilibiliDocumentReaderTest {

    private static final String BVID = "BV1StructuredTest";

    @Test
    void readSubtitlesPreservesPagesTracksAndCueTiming() {
        FakeHttpTransport transport = new FakeHttpTransport();
        BilibiliDocumentReader reader = new BilibiliDocumentReader(resource(), transport);

        List<BilibiliVideoSubtitles> videos = reader.readSubtitles();

        assertEquals(1, videos.size());
        BilibiliVideoSubtitles video = videos.get(0);
        assertEquals(BVID, video.bvid());
        assertEquals("结构化测试", video.title());
        assertEquals("测试说明", video.description());
        assertEquals(2, video.pages().size());

        BilibiliSubtitlePage firstPage = video.pages().get(0);
        assertEquals(101L, firstPage.cid());
        assertEquals(1, firstPage.page());
        assertEquals("开场", firstPage.part());
        assertEquals(2, firstPage.tracks().size());

        BilibiliSubtitleTrack chineseTrack = firstPage.tracks().get(0);
        assertEquals(11L, chineseTrack.id());
        assertEquals("zh-CN", chineseTrack.language());
        assertEquals("中文（中国）", chineseTrack.languageDescription());
        assertFalse(chineseTrack.locked());
        assertEquals(2, chineseTrack.cues().size());

        BilibiliSubtitleCue firstCue = chineseTrack.cues().get(0);
        assertEquals(new BigDecimal("0.12"), firstCue.from());
        assertEquals(new BigDecimal("2.81"), firstCue.to());
        assertEquals(1001L, firstCue.sid());
        assertEquals(2, firstCue.location());
        assertEquals("第一条", firstCue.content());

        BilibiliSubtitleTrack englishTrack = firstPage.tracks().get(1);
        assertEquals("en-US", englishTrack.language());
        assertEquals("First line", englishTrack.cues().get(0).content());

        BilibiliSubtitlePage secondPage = video.pages().get(1);
        assertEquals(202L, secondPage.cid());
        assertEquals(2, secondPage.page());
        assertEquals("正文", secondPage.part());
        assertTrue(new BigDecimal("1.50").compareTo(secondPage.tracks().get(0).cues().get(0).from()) == 0);

        assertThrows(UnsupportedOperationException.class, () -> video.pages().add(firstPage));
        assertThrows(UnsupportedOperationException.class, videos::clear);
        assertThrows(UnsupportedOperationException.class, () -> firstPage.tracks().clear());
        assertThrows(UnsupportedOperationException.class, () -> chineseTrack.cues().clear());
    }

    @Test
    void getKeepsLegacyDocumentContractAndOnlyDownloadsFirstTrack() {
        FakeHttpTransport transport = new FakeHttpTransport();
        BilibiliDocumentReader reader = new BilibiliDocumentReader(resource(), transport);

        List<Document> documents = reader.get();

        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertEquals(
                String.format("Video Title: 结构化测试, Description: 测试说明%nTranscript: 第一条\n第二条\n第三条"),
                document.getText()
        );
        assertEquals(Map.of(
                "bvid", BVID,
                "document_type", "content",
                "title", "结构化测试",
                "description", "测试说明"
        ), document.getMetadata());
        assertFalse(transport.requestedUrls.stream().anyMatch(url -> url.contains("subtitle/en-page1.json")));
    }

    private BilibiliResource resource() {
        BilibiliCredentials credentials = BilibiliCredentials.builder()
                .sessdata("test-sessdata")
                .biliJct("test-bili-jct")
                .build();
        return new BilibiliResource(BVID, credentials);
    }

    private static final class FakeHttpTransport implements BilibiliDocumentReader.HttpTransport {
        private final List<String> requestedUrls = new ArrayList<>();

        @Override
        public String get(BilibiliResource resource, String url) throws IOException {
            requestedUrls.add(url);
            if (url.startsWith("https://api.bilibili.com/x/web-interface/view")) {
                return """
                        {"data":{"title":"结构化测试","desc":"测试说明"}}
                        """;
            }
            if (url.startsWith("https://api.bilibili.com/x/player/pagelist")) {
                return """
                        {"data":[
                          {"cid":101,"page":1,"part":"开场"},
                          {"cid":202,"page":2,"part":"正文"}
                        ]}
                        """;
            }
            if (url.equals("https://api.bilibili.com/x/web-interface/nav")) {
                return """
                        {"data":{"wbi_img":{
                          "img_url":"https://i0.hdslb.com/bfs/wbi/abcdefghijklmnopqrstuvwxyzABCDEF.png",
                          "sub_url":"https://i0.hdslb.com/bfs/wbi/GHIJKLMNOPQRSTUVWXYZ0123456789ab.png"
                        }}}
                        """;
            }
            if (url.startsWith("https://api.bilibili.com/x/player/wbi/v2") && url.contains("cid=101")) {
                return """
                        {"data":{"subtitle":{"subtitles":[
                          {"id":11,"lan":"zh-CN","lan_doc":"中文（中国）","is_lock":false,"subtitle_url":"//subtitle/zh-page1.json"},
                          {"id":12,"lan":"en-US","lan_doc":"English","is_lock":false,"subtitle_url":"//subtitle/en-page1.json"}
                        ]}}}
                        """;
            }
            if (url.startsWith("https://api.bilibili.com/x/player/wbi/v2") && url.contains("cid=202")) {
                return """
                        {"data":{"subtitle":{"subtitles":[
                          {"id":21,"lan":"zh-CN","lan_doc":"中文（中国）","is_lock":false,"subtitle_url":"https://subtitle/zh-page2.json"}
                        ]}}}
                        """;
            }
            if (url.equals("https://subtitle/zh-page1.json")) {
                return """
                        {"body":[
                          {"from":0.12,"to":2.81,"sid":1001,"location":2,"content":" 第一条 "},
                          {"from":2.81,"to":5.40,"sid":1002,"location":2,"content":"第二条"}
                        ]}
                        """;
            }
            if (url.equals("https://subtitle/en-page1.json")) {
                return """
                        {"body":[
                          {"from":0.12,"to":2.81,"sid":2001,"location":2,"content":"First line"}
                        ]}
                        """;
            }
            if (url.equals("https://subtitle/zh-page2.json")) {
                return """
                        {"body":[
                          {"from":1.50,"to":4.00,"sid":3001,"location":2,"content":"第三条"}
                        ]}
                        """;
            }
            throw new IOException("Unexpected URL: " + url);
        }
    }

}
