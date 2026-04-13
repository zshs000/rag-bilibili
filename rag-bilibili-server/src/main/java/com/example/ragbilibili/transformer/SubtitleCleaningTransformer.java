package com.example.ragbilibili.transformer;

import com.example.ragbilibili.config.SubtitleCleaningProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 清洗自动字幕中的广告和低信息噪声。
 * <p>
 * 输出仍然保留按行的字幕边界，但判定阶段允许查看少量上下文窗口，
 * 用来识别被 ASR 拆成多行的广告口播。
 */
@Component
public class SubtitleCleaningTransformer implements DocumentTransformer {

    private static final String TRANSCRIPT_MARKER = "Transcript:";

    private final SubtitleCleaningProperties properties;
    private final List<Pattern> strongPatterns;
    private final List<Pattern> suspiciousPatterns;
    private final List<String> weakKeywords;

    public SubtitleCleaningTransformer(SubtitleCleaningProperties properties) {
        this.properties = properties;
        this.strongPatterns = compilePatterns(properties.getStrongPatterns());
        this.suspiciousPatterns = compilePatterns(properties.getSuspiciousPatterns());
        this.weakKeywords = normalizeKeywords(properties.getWeakKeywords());
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .map(this::cleanDocument)
                .collect(Collectors.toList());
    }

    private Document cleanDocument(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank() || !text.contains(TRANSCRIPT_MARKER)) {
            return document;
        }

        int markerIndex = text.indexOf(TRANSCRIPT_MARKER);
        String header = text.substring(0, markerIndex + TRANSCRIPT_MARKER.length());
        String transcript = text.substring(markerIndex + TRANSCRIPT_MARKER.length()).trim();

        List<String> keptSegments = cleanTranscriptSegments(splitSegments(transcript));
        String cleanedTranscript = String.join("\n", keptSegments).trim();
        String cleanedText = cleanedTranscript.isEmpty() ? header : header + "\n" + cleanedTranscript;

        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put("cleaned", true);
        metadata.put("subtitle_segment_count", keptSegments.size());

        return Document.builder()
                .id(document.getId())
                .text(cleanedText)
                .metadata(metadata)
                .build();
    }

    private List<String> splitSegments(String transcript) {
        String[] lines = transcript.split("\\R+");
        List<String> segments = new ArrayList<>(lines.length);
        for (String line : lines) {
            String normalized = normalizeSegment(line);
            if (!normalized.isEmpty()) {
                segments.add(normalized);
            }
        }
        return segments;
    }

    private List<String> cleanTranscriptSegments(List<String> segments) {
        boolean[] dropped = new boolean[segments.size()];
        List<String> kept = new ArrayList<>();
        String previousFingerprint = null;

        for (int i = 0; i < segments.size(); i++) {
            if (dropped[i]) {
                continue;
            }

            int matchedWindowSize = findDropWindowSize(segments, i);
            if (matchedWindowSize > 0) {
                markDropped(dropped, i, matchedWindowSize);
                continue;
            }

            String segment = segments.get(i);
            String fingerprint = fingerprint(segment);
            if (fingerprint.equals(previousFingerprint)) {
                continue;
            }

            kept.add(segment);
            previousFingerprint = fingerprint;
        }

        return kept;
    }

    /**
     * 先判单行，再对“当前起始行本身可疑”的内容做扩窗联判，
     * 避免把前一条正常字幕和后一条广告一起误删。
     */
    private int findDropWindowSize(List<String> segments, int startIndex) {
        int remaining = segments.size() - startIndex;
        int maxWindowSize = Math.min(properties.getMaxWindowSize(), remaining);
        String currentSegment = segments.get(startIndex);

        if (shouldDropWindow(currentSegment)) {
            return 1;
        }

        if (maxWindowSize <= 1 || !shouldExpandWindow(currentSegment)) {
            return 0;
        }

        int matchedWindowSize = 0;
        String twoLineWindow = joinSegments(segments, startIndex, 2);
        if (shouldDropWindow(twoLineWindow)) {
            matchedWindowSize = 2;
        }

        if (maxWindowSize <= 2) {
            return matchedWindowSize;
        }

        boolean canExpandBeyondTwoLines = !properties.isConditionalExpandTo3()
                || shouldExpandWindow(twoLineWindow);
        if (!canExpandBeyondTwoLines) {
            return matchedWindowSize;
        }

        for (int windowSize = 3; windowSize <= maxWindowSize; windowSize++) {
            if (shouldDropWindow(joinSegments(segments, startIndex, windowSize))) {
                matchedWindowSize = windowSize;
            }
        }

        return matchedWindowSize;
    }

    private boolean shouldDropWindow(String windowText) {
        if (windowText.isBlank()) {
            return true;
        }

        for (Pattern pattern : strongPatterns) {
            if (pattern.matcher(windowText).find()) {
                return true;
            }
        }

        return countWeakHits(windowText) >= properties.getWeakKeywordThreshold();
    }

    /**
     * 可疑信号只决定“要不要继续看下一行”，不直接等价于广告删除。
     */
    private boolean shouldExpandWindow(String windowText) {
        if (countWeakHits(windowText) > 0) {
            return true;
        }

        for (Pattern pattern : suspiciousPatterns) {
            if (pattern.matcher(windowText).find()) {
                return true;
            }
        }

        return false;
    }

    private void markDropped(boolean[] dropped, int startIndex, int windowSize) {
        for (int i = startIndex; i < startIndex + windowSize; i++) {
            dropped[i] = true;
        }
    }

    private String joinSegments(List<String> segments, int startIndex, int windowSize) {
        return String.join(" ", segments.subList(startIndex, startIndex + windowSize));
    }

    /**
     * 长词优先并在命中后做遮罩，避免“转转”和“转转二手”这类重叠词重复计数。
     */
    private int countWeakHits(String segment) {
        int weakHits = 0;
        String remaining = segment.toLowerCase(Locale.ROOT);
        for (String keyword : weakKeywords) {
            if (remaining.contains(keyword)) {
                weakHits++;
                remaining = maskKeyword(remaining, keyword);
            }
        }
        return weakHits;
    }

    private String maskKeyword(String source, String keyword) {
        StringBuilder builder = new StringBuilder(source);
        int fromIndex = 0;
        int index;
        while ((index = source.indexOf(keyword, fromIndex)) >= 0) {
            for (int i = index; i < index + keyword.length(); i++) {
                builder.setCharAt(i, ' ');
            }
            fromIndex = index + keyword.length();
        }
        return builder.toString();
    }

    private String normalizeSegment(String segment) {
        return segment
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String fingerprint(String segment) {
        return segment.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private List<Pattern> compilePatterns(List<String> patternTexts) {
        return patternTexts.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            normalized.add(keyword.toLowerCase(Locale.ROOT));
        }
        return normalized.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }
}