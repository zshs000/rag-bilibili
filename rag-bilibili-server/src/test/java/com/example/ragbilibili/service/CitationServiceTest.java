package com.example.ragbilibili.service;

import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.RetrievedChunkSource;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.util.BilibiliJumpUrlBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitationServiceTest {
    @Mock
    private VectorMappingMapper vectorMappingMapper;

    @Test
    void shouldRestoreRetrievalOrderAndPersistOnlyValidCitations() {
        CitationService service = new CitationService(vectorMappingMapper, new BilibiliJumpUrlBuilder());
        RetrievedChunkSource second = source("vector-2", "BV1iH3763Ezm", 2, 20_000L);
        RetrievedChunkSource first = source("vector-1", "BV1GJ411x7h7", 1, 10_000L);
        when(vectorMappingMapper.selectRetrievedSourcesByVectorIds(7L, List.of("vector-1", "vector-2")))
                .thenReturn(List.of(second, first));

        List<RetrievedSourceCandidate> candidates = service.resolveCandidates(
                List.of(new Document("vector-1", "first", Map.of()),
                        new Document("vector-2", "second", Map.of())),
                7L);
        List<MessageSource> cited = service.extractCitedSources(
                "结论一 [2]，重复引用 [2]，无效引用 [9]。", candidates);

        assertEquals(List.of("vector-1", "vector-2"),
                candidates.stream().map(RetrievedSourceCandidate::vectorId).toList());
        assertEquals(1, cited.size());
        assertEquals(2, cited.get(0).getCitationIndex());
        assertEquals("vector-2", cited.get(0).getVectorId());
    }

    @Test
    void shouldKeepLegacyDocumentInContextWithoutMakingItCitable() {
        CitationService service = new CitationService(vectorMappingMapper, new BilibiliJumpUrlBuilder());
        RetrievedChunkSource legacy = source("legacy-vector", "BV1iH3763Ezm", 1, 0L);
        legacy.setStartTimeMs(null);
        legacy.setEndTimeMs(null);
        when(vectorMappingMapper.selectRetrievedSourcesByVectorIds(7L, List.of("legacy-vector")))
                .thenReturn(List.of(legacy));
        Document document = new Document("legacy-vector", "旧字幕仍应参与回答", Map.of());

        RetrievedSourceResolution resolution = service.resolve(List.of(document), 7L);
        String context = service.buildContext(List.of(document), resolution);

        assertEquals(List.of(), resolution.candidates());
        assertTrue(context.contains("旧字幕仍应参与回答"));
        assertTrue(context.contains("不得为其添加引用编号"));
    }

    @Test
    void shouldNotPutUnauthorizedOrUnmappedDocumentBackIntoContext() {
        CitationService service = new CitationService(vectorMappingMapper, new BilibiliJumpUrlBuilder());
        Document document = new Document("foreign-vector", "其他用户的字幕", Map.of());
        when(vectorMappingMapper.selectRetrievedSourcesByVectorIds(7L, List.of("foreign-vector")))
                .thenReturn(List.of());

        RetrievedSourceResolution resolution = service.resolve(List.of(document), 7L);
        String context = service.buildContext(List.of(document), resolution);

        assertEquals("没有找到相关的视频内容。", context);
    }

    private RetrievedChunkSource source(String vectorId, String bvid, int page, long startTimeMs) {
        RetrievedChunkSource source = new RetrievedChunkSource();
        source.setVectorId(vectorId);
        source.setBvid(bvid);
        source.setVideoTitle("标题");
        source.setPageNumber(page);
        source.setStartTimeMs(startTimeMs);
        source.setEndTimeMs(startTimeMs + 5_000L);
        source.setSnippet("字幕内容");
        return source;
    }
}
