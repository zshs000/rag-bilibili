package com.example.ragbilibili.transformer;

import com.example.ragbilibili.config.SubtitleCleaningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleCleaningTransformerTest {

    private final SubtitleCleaningTransformer transformer = new SubtitleCleaningTransformer(new SubtitleCleaningProperties());

    @Test
    void shouldRemoveStrongAdSegmentsAndKeepTranscriptStructure() {
        Document input = Document.builder()
                .id("doc-1")
                .text("Video Title: Java\nTranscript:\n大家好 今天讲锁优化\n本期视频由转转二手赞助\n点击下方链接领取优惠\n我们继续看偏向锁撤销")
                .metadata(Map.of("title", "Java"))
                .build();

        Document cleaned = transformer.apply(List.of(input)).get(0);

        assertTrue(cleaned.getText().contains("大家好 今天讲锁优化"));
        assertTrue(cleaned.getText().contains("我们继续看偏向锁撤销"));
        assertFalse(cleaned.getText().contains("转转二手"));
        assertFalse(cleaned.getText().contains("点击下方链接"));
        assertEquals(true, cleaned.getMetadata().get("cleaned"));
        assertEquals(2, cleaned.getMetadata().get("subtitle_segment_count"));
    }

    @Test
    void shouldRemoveCrossLineAdSegmentsWithDefaultWindowRules() {
        Document input = Document.builder()
                .id("doc-2")
                .text("Video Title: Java\nTranscript:\n今天我们讲 synchronized\n感谢转转二手\n对本期视频的支持\n点击下方\n链接领取优惠\n最后看锁膨胀")
                .metadata(Map.of("title", "Java"))
                .build();

        Document cleaned = transformer.apply(List.of(input)).get(0);

        assertTrue(cleaned.getText().contains("今天我们讲 synchronized"));
        assertTrue(cleaned.getText().contains("最后看锁膨胀"));
        assertFalse(cleaned.getText().contains("感谢转转二手"));
        assertFalse(cleaned.getText().contains("对本期视频的支持"));
        assertFalse(cleaned.getText().contains("点击下方"));
        assertFalse(cleaned.getText().contains("链接领取优惠"));
        assertEquals(2, cleaned.getMetadata().get("subtitle_segment_count"));
    }

    @Test
    void shouldRespectConfiguredMaxWindowSizeWhenThreeLinesAreRequired() {
        SubtitleCleaningProperties properties = new SubtitleCleaningProperties();
        properties.setMaxWindowSize(2);
        SubtitleCleaningTransformer twoLineTransformer = new SubtitleCleaningTransformer(properties);

        Document input = Document.builder()
                .id("doc-3")
                .text("Video Title: Demo\nTranscript:\n今天开始讲 JVM\n感谢\n转转二手\n对本期视频的支持\n继续看逃逸分析")
                .metadata(Map.of())
                .build();

        Document cleaned = twoLineTransformer.apply(List.of(input)).get(0);

        assertTrue(cleaned.getText().contains("感谢"));
        assertTrue(cleaned.getText().contains("转转二手"));
        assertTrue(cleaned.getText().contains("对本期视频的支持"));
        assertEquals(5, cleaned.getMetadata().get("subtitle_segment_count"));
    }

    @Test
    void shouldOnlyDeduplicateAdjacentRepeatedSegments() {
        Document input = Document.builder()
                .id("doc-4")
                .text("Video Title: Demo\nTranscript:\n我们来看第一个例子\n我们来看第一个例子\n这里会发生锁升级\n我们来看第一个例子")
                .metadata(Map.of())
                .build();

        Document cleaned = transformer.apply(List.of(input)).get(0);

        assertTrue(cleaned.getText().contains("这里会发生锁升级"));
        assertEquals(2, countOccurrences(cleaned.getText(), "我们来看第一个例子"));
    }

    @Test
    void shouldKeepShortMeaningfulSegments() {
        Document input = Document.builder()
                .id("doc-5")
                .text("Video Title: Demo\nTranscript:\n有锁\n会阻塞\n可重入")
                .metadata(Map.of())
                .build();

        Document cleaned = transformer.apply(List.of(input)).get(0);

        assertTrue(cleaned.getText().contains("有锁"));
        assertTrue(cleaned.getText().contains("会阻塞"));
        assertTrue(cleaned.getText().contains("可重入"));
        assertEquals(3, cleaned.getMetadata().get("subtitle_segment_count"));
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}