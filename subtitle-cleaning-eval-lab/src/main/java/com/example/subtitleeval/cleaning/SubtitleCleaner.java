package com.example.subtitleeval.cleaning;

import com.example.subtitleeval.model.EvalDocument;

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

public class SubtitleCleaner {
    private static final String TRANSCRIPT_MARKER = "Transcript:";

    private final SubtitleCleaningProperties properties;
    private final List<Pattern> strongPatterns;
    private final List<Pattern> suspiciousPatterns;
    private final List<String> weakKeywords;

    public SubtitleCleaner(SubtitleCleaningProperties properties) {
        this.properties = properties;
        this.strongPatterns = compilePatterns(properties.getStrongPatterns());
        this.suspiciousPatterns = compilePatterns(properties.getSuspiciousPatterns());
        this.weakKeywords = normalizeKeywords(properties.getWeakKeywords());
    }

    public List<EvalDocument> apply(List<EvalDocument> documents) {
        return documents.stream()
                .map(this::cleanDocument)
                .collect(Collectors.toList());
    }

    public EvalDocument cleanDocument(EvalDocument document) {
        return inspect(document).getCleanedDocument();
    }

    public CleaningTrace inspect(EvalDocument document) {
        String text = document.getText();
        if (text == null || text.isBlank() || !text.contains(TRANSCRIPT_MARKER)) {
            return new CleaningTrace(document, document, List.of(), List.of(), List.of());
        }

        int markerIndex = text.indexOf(TRANSCRIPT_MARKER);
        String header = text.substring(0, markerIndex + TRANSCRIPT_MARKER.length());
        String transcript = text.substring(markerIndex + TRANSCRIPT_MARKER.length()).trim();

        List<String> segments = splitSegments(transcript);
        TraceComputation computation = cleanTranscriptSegmentsWithTrace(segments);
        String cleanedTranscript = String.join("\n", computation.keptSegments()).trim();
        String cleanedText = cleanedTranscript.isEmpty() ? header : header + "\n" + cleanedTranscript;

        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put("cleaned", true);
        metadata.put("subtitle_segment_count", computation.keptSegments().size());

        EvalDocument cleanedDocument = EvalDocument.builder()
                .id(document.getId())
                .text(cleanedText)
                .metadata(metadata)
                .build();

        return new CleaningTrace(
                document,
                cleanedDocument,
                segments,
                computation.keptSegments(),
                computation.decisions()
        );
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

    private TraceComputation cleanTranscriptSegmentsWithTrace(List<String> segments) {
        boolean[] dropped = new boolean[segments.size()];
        List<String> kept = new ArrayList<>();
        List<CleaningTrace.Decision> decisions = new ArrayList<>();
        String previousFingerprint = null;

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            CleaningTrace.DecisionBuilder builder = new CleaningTrace.DecisionBuilder(i + 1, segment);

            if (dropped[i]) {
                builder.action(CleaningTrace.Action.SKIP_AS_ALREADY_DROPPED)
                        .reason("该行已被前面的广告窗口一并删除。");
                decisions.add(builder.build());
                continue;
            }

            WindowDecision matchedWindow = findDropWindowDecision(segments, i);
            if (matchedWindow.windowSize() > 0) {
                markDropped(dropped, i, matchedWindow.windowSize());
                builder.action(CleaningTrace.Action.DROP);
                matchedWindow.reasons().forEach(builder::reason);
                if (matchedWindow.windowSize() > 1) {
                    builder.reason("最终删除窗口: 第 " + (i + 1) + " 行到第 " + (i + matchedWindow.windowSize()) + " 行。");
                } else {
                    builder.reason("最终删除窗口: 仅删除当前第 " + (i + 1) + " 行。");
                }
                decisions.add(builder.build());
                continue;
            }

            String fingerprint = fingerprint(segment);
            if (fingerprint.equals(previousFingerprint)) {
                builder.action(CleaningTrace.Action.SKIP_AS_ADJACENT_DUPLICATE)
                        .reason("与上一条保留字幕相邻重复，按现有逻辑跳过。");
                decisions.add(builder.build());
                continue;
            }

            kept.add(segment);
            previousFingerprint = fingerprint;
            builder.action(CleaningTrace.Action.KEEP)
                    .reason("未命中删除规则，保留。");
            decisions.add(builder.build());
        }

        return new TraceComputation(kept, decisions);
    }

    private WindowDecision findDropWindowDecision(List<String> segments, int startIndex) {
        int remaining = segments.size() - startIndex;
        int maxWindowSize = Math.min(properties.getMaxWindowSize(), remaining);
        String currentSegment = segments.get(startIndex);

        DropEvaluation singleLineEvaluation = evaluateDropWindow(currentSegment);
        if (singleLineEvaluation.shouldDrop()) {
            List<String> reasons = new ArrayList<>();
            reasons.add("单行判断命中删除规则。");
            reasons.addAll(singleLineEvaluation.explain("单行"));
            return new WindowDecision(1, reasons);
        }

        ExpandEvaluation singleLineExpandEvaluation = evaluateExpandWindow(currentSegment);
        if (maxWindowSize <= 1 || !singleLineExpandEvaluation.shouldExpand()) {
            List<String> reasons = new ArrayList<>();
            reasons.add("单行判断未命中删除规则。");
            reasons.addAll(singleLineEvaluation.explain("单行"));
            if (!singleLineExpandEvaluation.shouldExpand()) {
                reasons.add("当前起始行不可疑，不继续扩窗。");
            }
            return new WindowDecision(0, reasons);
        }

        int matchedWindowSize = 0;
        List<String> reasons = new ArrayList<>();
        reasons.add("单行判断未命中删除规则。");
        reasons.addAll(singleLineEvaluation.explain("单行"));
        reasons.add("当前行可疑，继续尝试扩窗。");
        reasons.addAll(singleLineExpandEvaluation.explain("单行可疑信号"));

        String twoLineWindow = joinSegments(segments, startIndex, 2);
        DropEvaluation twoLineEvaluation = evaluateDropWindow(twoLineWindow);
        if (twoLineEvaluation.shouldDrop()) {
            matchedWindowSize = 2;
            reasons.add("两行窗口命中删除规则。");
            reasons.addAll(twoLineEvaluation.explain("两行窗口"));
        } else {
            reasons.add("两行窗口未命中删除规则。");
            reasons.addAll(twoLineEvaluation.explain("两行窗口"));
        }

        if (maxWindowSize <= 2) {
            return new WindowDecision(matchedWindowSize, reasons);
        }

        ExpandEvaluation twoLineExpandEvaluation = evaluateExpandWindow(twoLineWindow);
        boolean canExpandBeyondTwoLines = !properties.isConditionalExpandTo3()
                || twoLineExpandEvaluation.shouldExpand();
        if (!canExpandBeyondTwoLines) {
            reasons.add("两行窗口虽然未充分可疑，条件扩窗关闭，本轮不再继续扩到第三行。");
            return new WindowDecision(matchedWindowSize, reasons);
        }

        if (properties.isConditionalExpandTo3()) {
            reasons.add("两行窗口仍然可疑，继续向第 3 行扩窗。");
            reasons.addAll(twoLineExpandEvaluation.explain("两行窗口可疑信号"));
        } else {
            reasons.add("conditionalExpandTo3=false，允许继续扩窗。");
        }

        for (int windowSize = 3; windowSize <= maxWindowSize; windowSize++) {
            String windowText = joinSegments(segments, startIndex, windowSize);
            DropEvaluation evaluation = evaluateDropWindow(windowText);
            if (evaluation.shouldDrop()) {
                matchedWindowSize = windowSize;
                reasons.add(windowSize + " 行窗口命中删除规则。");
                reasons.addAll(evaluation.explain(windowSize + " 行窗口"));
            } else {
                reasons.add(windowSize + " 行窗口未命中删除规则。");
                reasons.addAll(evaluation.explain(windowSize + " 行窗口"));
            }
        }

        return new WindowDecision(matchedWindowSize, reasons);
    }

    private DropEvaluation evaluateDropWindow(String windowText) {
        if (windowText.isBlank()) {
            return new DropEvaluation(true, List.of("空白窗口按现有逻辑直接删除。"), List.of(), 0);
        }

        List<String> matchedStrongPatterns = new ArrayList<>();
        for (Pattern pattern : strongPatterns) {
            if (pattern.matcher(windowText).find()) {
                matchedStrongPatterns.add(pattern.pattern());
            }
        }

        WeakHitResult weakHitResult = collectWeakHits(windowText);
        boolean shouldDrop = !matchedStrongPatterns.isEmpty()
                || weakHitResult.hitCount() >= properties.getWeakKeywordThreshold();

        return new DropEvaluation(shouldDrop, matchedStrongPatterns, weakHitResult.hitKeywords(), weakHitResult.hitCount());
    }

    private ExpandEvaluation evaluateExpandWindow(String windowText) {
        WeakHitResult weakHitResult = collectWeakHits(windowText);
        List<String> matchedSuspiciousPatterns = new ArrayList<>();
        for (Pattern pattern : suspiciousPatterns) {
            if (pattern.matcher(windowText).find()) {
                matchedSuspiciousPatterns.add(pattern.pattern());
            }
        }

        boolean shouldExpand = weakHitResult.hitCount() > 0 || !matchedSuspiciousPatterns.isEmpty();
        return new ExpandEvaluation(shouldExpand, matchedSuspiciousPatterns, weakHitResult.hitKeywords(), weakHitResult.hitCount());
    }

    private void markDropped(boolean[] dropped, int startIndex, int windowSize) {
        for (int i = startIndex; i < startIndex + windowSize; i++) {
            dropped[i] = true;
        }
    }

    private String joinSegments(List<String> segments, int startIndex, int windowSize) {
        return String.join(" ", segments.subList(startIndex, startIndex + windowSize));
    }

    private WeakHitResult collectWeakHits(String segment) {
        int weakHits = 0;
        String remaining = segment.toLowerCase(Locale.ROOT);
        List<String> matchedKeywords = new ArrayList<>();
        for (String keyword : weakKeywords) {
            if (remaining.contains(keyword)) {
                weakHits++;
                matchedKeywords.add(keyword);
                remaining = maskKeyword(remaining, keyword);
            }
        }
        return new WeakHitResult(weakHits, matchedKeywords);
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

    private record TraceComputation(List<String> keptSegments, List<CleaningTrace.Decision> decisions) {
    }

    private record WeakHitResult(int hitCount, List<String> hitKeywords) {
    }

    private record WindowDecision(int windowSize, List<String> reasons) {
    }

    private record DropEvaluation(boolean shouldDrop,
                                  List<String> matchedStrongPatterns,
                                  List<String> matchedWeakKeywords,
                                  int weakHitCount) {
        private List<String> explain(String label) {
            List<String> reasons = new ArrayList<>();
            reasons.add(label + " 强规则命中: "
                    + (matchedStrongPatterns.isEmpty() ? "无" : matchedStrongPatterns));
            reasons.add(label + " 弱关键词命中数: " + weakHitCount
                    + (matchedWeakKeywords.isEmpty() ? "" : "，命中项=" + matchedWeakKeywords));
            return reasons;
        }
    }

    private record ExpandEvaluation(boolean shouldExpand,
                                    List<String> matchedSuspiciousPatterns,
                                    List<String> matchedWeakKeywords,
                                    int weakHitCount) {
        private List<String> explain(String label) {
            List<String> reasons = new ArrayList<>();
            reasons.add(label + " suspicious 命中: "
                    + (matchedSuspiciousPatterns.isEmpty() ? "无" : matchedSuspiciousPatterns));
            reasons.add(label + " 弱关键词命中数: " + weakHitCount
                    + (matchedWeakKeywords.isEmpty() ? "" : "，命中项=" + matchedWeakKeywords));
            return reasons;
        }
    }
}
