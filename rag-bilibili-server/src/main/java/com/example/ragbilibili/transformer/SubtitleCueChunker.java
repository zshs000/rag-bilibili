package com.example.ragbilibili.transformer;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitleCue;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitlePage;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitleTrack;
import com.example.ragbilibili.config.SubtitleChunkingProperties;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SubtitleCueChunker {
    private final SubtitleChunkingProperties properties;
    private final TokenCountEstimator tokenCountEstimator;

    public SubtitleCueChunker(SubtitleChunkingProperties properties) {
        this(properties, new JTokkitTokenCountEstimator());
    }

    SubtitleCueChunker(SubtitleChunkingProperties properties, TokenCountEstimator tokenCountEstimator) {
        this.properties = properties;
        this.tokenCountEstimator = tokenCountEstimator;
    }

    public List<TimestampedSubtitleChunk> split(BilibiliSubtitlePage page,
                                                BilibiliSubtitleTrack track,
                                                List<BilibiliSubtitleCue> cues) {
        if (cues.isEmpty()) {
            return List.of();
        }

        int targetTokens = requirePositive(properties.getTargetTokens(), "targetTokens");
        int minTokens = requirePositive(properties.getMinTokens(), "minTokens");
        int maxTokens = requirePositive(properties.getMaxTokens(), "maxTokens");
        int overlapCues = Math.max(0, properties.getOverlapCues());
        int totalTokens = estimate(cues, 0, cues.size());
        int minimumChunkCount = divideRoundingUp(totalTokens, maxTokens);
        int maximumChunkCount = Math.max(1, totalTokens / minTokens);
        int idealChunkCount = Math.max(1, Math.round((float) totalTokens / targetTokens));
        int chunkCount = Math.max(minimumChunkCount, Math.min(idealChunkCount, maximumChunkCount));

        List<Integer> boundaries = partition(cues, chunkCount);
        List<TimestampedSubtitleChunk> chunks = new ArrayList<>(boundaries.size());
        int uniqueStart = 0;
        for (int uniqueEnd : boundaries) {
            int chunkStart = overlapStart(cues, uniqueStart, uniqueEnd, overlapCues, maxTokens);
            List<BilibiliSubtitleCue> chunkCues = cues.subList(chunkStart, uniqueEnd);
            chunks.add(new TimestampedSubtitleChunk(
                    page.cid(),
                    page.page(),
                    track.language(),
                    toMilliseconds(chunkCues.get(0).from()),
                    toMilliseconds(chunkCues.get(chunkCues.size() - 1).to()),
                    joinContent(chunkCues)
            ));
            uniqueStart = uniqueEnd;
        }
        return List.copyOf(chunks);
    }

    private List<Integer> partition(List<BilibiliSubtitleCue> cues, int requestedChunkCount) {
        int chunkCount = Math.min(requestedChunkCount, cues.size());
        int totalTokens = estimate(cues, 0, cues.size());
        List<Integer> boundaries = new ArrayList<>(chunkCount);
        int start = 0;

        for (int chunkIndex = 0; chunkIndex < chunkCount - 1; chunkIndex++) {
            int chunksRemaining = chunkCount - chunkIndex;
            int remainingTokens = estimate(cues, start, cues.size());
            int desiredTokens = Math.max(1, Math.round((float) remainingTokens / chunksRemaining));
            int lastAllowedEnd = cues.size() - (chunksRemaining - 1);
            int bestEnd = start + 1;
            int bestDistance = Integer.MAX_VALUE;

            for (int end = start + 1; end <= lastAllowedEnd; end++) {
                int tokens = estimate(cues, start, end);
                if (tokens > properties.getMaxTokens() && end > start + 1) {
                    break;
                }
                int distance = Math.abs(tokens - desiredTokens);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    bestEnd = end;
                }
            }
            boundaries.add(bestEnd);
            start = bestEnd;
        }
        boundaries.add(cues.size());
        return boundaries;
    }

    private int overlapStart(List<BilibiliSubtitleCue> cues,
                             int uniqueStart,
                             int uniqueEnd,
                             int overlapCues,
                             int maxTokens) {
        int chunkStart = uniqueStart;
        for (int count = 0; count < overlapCues && chunkStart > 0; count++) {
            int candidate = chunkStart - 1;
            if (estimate(cues, candidate, uniqueEnd) > maxTokens) {
                break;
            }
            chunkStart = candidate;
        }
        return chunkStart;
    }

    private int estimate(List<BilibiliSubtitleCue> cues, int start, int end) {
        return tokenCountEstimator.estimate(joinContent(cues.subList(start, end)));
    }

    private String joinContent(List<BilibiliSubtitleCue> cues) {
        return String.join("\n", cues.stream().map(BilibiliSubtitleCue::content).toList());
    }

    private long toMilliseconds(BigDecimal seconds) {
        if (seconds == null) {
            return 0L;
        }
        return seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private int divideRoundingUp(int dividend, int divisor) {
        return Math.max(1, (dividend + divisor - 1) / divisor);
    }

    private int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}
