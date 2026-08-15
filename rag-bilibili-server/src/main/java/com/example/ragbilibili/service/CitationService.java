package com.example.ragbilibili.service;

import com.example.ragbilibili.dto.response.MessageSourceResponse;
import com.example.ragbilibili.entity.MessageSource;
import com.example.ragbilibili.entity.RetrievedChunkSource;
import com.example.ragbilibili.mapper.VectorMappingMapper;
import com.example.ragbilibili.util.BilibiliJumpUrlBuilder;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CitationService {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    private final VectorMappingMapper vectorMappingMapper;
    private final BilibiliJumpUrlBuilder jumpUrlBuilder;

    public CitationService(VectorMappingMapper vectorMappingMapper, BilibiliJumpUrlBuilder jumpUrlBuilder) {
        this.vectorMappingMapper = vectorMappingMapper;
        this.jumpUrlBuilder = jumpUrlBuilder;
    }

    public List<RetrievedSourceCandidate> resolveCandidates(List<Document> documents, Long userId) {
        return resolve(documents, userId).candidates();
    }

    public RetrievedSourceResolution resolve(List<Document> documents, Long userId) {
        if (documents == null || documents.isEmpty()) {
            return new RetrievedSourceResolution(List.of(), Set.of());
        }
        List<String> vectorIds = documents.stream()
                .map(Document::getId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (vectorIds.isEmpty()) {
            return new RetrievedSourceResolution(List.of(), Set.of());
        }
        List<RetrievedChunkSource> rows = vectorMappingMapper
                .selectRetrievedSourcesByVectorIds(userId, vectorIds);
        if (rows == null || rows.isEmpty()) {
            return new RetrievedSourceResolution(List.of(), Set.of());
        }
        Map<String, RetrievedChunkSource> byVectorId = rows.stream()
                .collect(Collectors.toMap(RetrievedChunkSource::getVectorId, Function.identity(), (first, ignored) -> first));
        List<RetrievedSourceCandidate> candidates = new ArrayList<>();
        Set<String> authorizedUncitableVectorIds = new LinkedHashSet<>();
        for (String vectorId : vectorIds) {
            RetrievedChunkSource row = byVectorId.get(vectorId);
            if (row == null) {
                continue;
            }
            if (row.getBvid() == null || row.getStartTimeMs() == null || row.getEndTimeMs() == null) {
                authorizedUncitableVectorIds.add(vectorId);
                continue;
            }
            candidates.add(new RetrievedSourceCandidate(
                    candidates.size() + 1,
                    row.getVectorId(),
                    row.getBvid(),
                    row.getVideoTitle(),
                    row.getCid(),
                    row.getPageNumber() == null ? 1 : row.getPageNumber(),
                    row.getStartTimeMs(),
                    row.getEndTimeMs(),
                    row.getSnippet()));
        }
        return new RetrievedSourceResolution(
                List.copyOf(candidates), Set.copyOf(authorizedUncitableVectorIds));
    }

    public String buildContext(List<RetrievedSourceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "没有找到相关的视频内容。";
        }
        StringBuilder context = new StringBuilder("以下是相关的视频字幕来源：\n\n");
        for (RetrievedSourceCandidate source : candidates) {
            context.append("[来源 ").append(source.citationIndex()).append("]\n")
                    .append("视频：").append(source.videoTitle()).append("\n")
                    .append("BV号：").append(source.bvid()).append("，分P：")
                    .append(source.pageNumber()).append("，时间：")
                    .append(source.startTimeMs()).append("ms-")
                    .append(source.endTimeMs()).append("ms\n")
                    .append(source.snippet()).append("\n\n");
        }
        return context.toString();
    }

    public String buildContext(List<Document> documents, RetrievedSourceResolution resolution) {
        List<RetrievedSourceCandidate> candidates = resolution.candidates();
        String numberedContext = buildContext(candidates);
        if (documents == null || documents.isEmpty()) {
            return numberedContext;
        }
        List<Document> legacyDocuments = documents.stream()
                .filter(document -> document.getId() != null
                        && resolution.authorizedUncitableVectorIds().contains(document.getId()))
                .toList();
        if (legacyDocuments.isEmpty()) {
            return numberedContext;
        }

        StringBuilder context = new StringBuilder();
        if (!candidates.isEmpty()) {
            context.append(numberedContext);
        }
        context.append("以下旧字幕片段缺少时间信息，可以用于回答，但不得为其添加引用编号：\n\n");
        for (Document document : legacyDocuments) {
            context.append("[未编号片段]\n")
                    .append(document.getText())
                    .append("\n\n");
        }
        return context.toString();
    }

    public List<MessageSource> extractCitedSources(String answer, List<RetrievedSourceCandidate> candidates) {
        if (answer == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<Integer> citedIndexes = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                citedIndexes.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 超出整数范围的伪引用不是有效来源。
            }
        }
        return candidates.stream()
                .filter(candidate -> citedIndexes.contains(candidate.citationIndex()))
                .map(this::toEntity)
                .toList();
    }

    public List<MessageSourceResponse> toResponses(List<MessageSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        return sources.stream().map(source -> {
            MessageSourceResponse response = new MessageSourceResponse();
            response.setIndex(source.getCitationIndex());
            response.setBvid(source.getBvid());
            response.setVideoTitle(source.getVideoTitle());
            response.setPageNumber(source.getPageNumber());
            response.setStartTimeMs(source.getStartTimeMs());
            response.setEndTimeMs(source.getEndTimeMs());
            response.setSnippet(source.getSnippet());
            response.setJumpUrl(jumpUrlBuilder.build(source.getBvid(), source.getPageNumber(), source.getStartTimeMs()));
            return response;
        }).toList();
    }

    private MessageSource toEntity(RetrievedSourceCandidate candidate) {
        MessageSource source = new MessageSource();
        source.setCitationIndex(candidate.citationIndex());
        source.setVectorId(candidate.vectorId());
        source.setBvid(candidate.bvid());
        source.setVideoTitle(candidate.videoTitle());
        source.setCid(candidate.cid());
        source.setPageNumber(candidate.pageNumber());
        source.setStartTimeMs(candidate.startTimeMs());
        source.setEndTimeMs(candidate.endTimeMs());
        source.setSnippet(candidate.snippet());
        return source;
    }
}
