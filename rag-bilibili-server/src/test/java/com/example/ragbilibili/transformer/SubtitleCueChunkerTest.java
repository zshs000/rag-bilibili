package com.example.ragbilibili.transformer;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitleCue;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitlePage;
import com.alibaba.cloud.ai.reader.bilibili.BilibiliSubtitleTrack;
import com.example.ragbilibili.config.SubtitleChunkingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleCueChunkerTest {

    @Test
    void shouldBalanceChunksOnCueBoundariesAndOverlapTwoCues() {
        SubtitleChunkingProperties properties = new SubtitleChunkingProperties();
        SubtitleCueChunker chunker = new SubtitleCueChunker(properties, new WordTokenEstimator());
        List<BilibiliSubtitleCue> cues = cues(10, 70);
        BilibiliSubtitleTrack track = new BilibiliSubtitleTrack(1L, "ai-zh", "中文（自动生成）", false, cues);
        BilibiliSubtitlePage page = new BilibiliSubtitlePage(123L, 1, "测试", List.of(track));

        List<TimestampedSubtitleChunk> chunks = chunker.split(page, track, cues);

        assertEquals(2, chunks.size());
        assertEquals(350, tokenCount(chunks.get(0).text()));
        assertEquals(490, tokenCount(chunks.get(1).text()));
        assertEquals(0L, chunks.get(0).startTimeMs());
        assertEquals(5000L, chunks.get(0).endTimeMs());
        assertEquals(3000L, chunks.get(1).startTimeMs());
        assertEquals(10000L, chunks.get(1).endTimeMs());
        assertTrue(chunks.get(1).text().startsWith("cue-3 "));
        assertFalse(chunks.get(0).text().contains("cue-5 "));
    }

    private List<BilibiliSubtitleCue> cues(int count, int tokensPerCue) {
        List<BilibiliSubtitleCue> cues = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String text = "cue-" + index + " " + "word ".repeat(tokensPerCue - 1).trim();
            cues.add(new BilibiliSubtitleCue(
                    BigDecimal.valueOf(index),
                    BigDecimal.valueOf(index + 1L),
                    (long) index,
                    2,
                    text
            ));
        }
        return cues;
    }

    private int tokenCount(String text) {
        return new WordTokenEstimator().estimate(text);
    }

    private static final class WordTokenEstimator implements TokenCountEstimator {
        @Override
        public int estimate(String text) {
            return text.isBlank() ? 0 : text.trim().split("\\s+").length;
        }

        @Override
        public int estimate(org.springframework.ai.content.MediaContent content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int estimate(Iterable<org.springframework.ai.content.MediaContent> contents) {
            throw new UnsupportedOperationException();
        }
    }
}
