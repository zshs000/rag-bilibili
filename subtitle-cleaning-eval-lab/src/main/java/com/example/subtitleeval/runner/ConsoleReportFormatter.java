package com.example.subtitleeval.runner;

import com.example.subtitleeval.cleaning.CleaningTrace;
import com.example.subtitleeval.model.EvalDocument;

import java.util.List;

public class ConsoleReportFormatter {
    public String format(EvalDocument originalDocument, CleaningTrace trace) {
        StringBuilder builder = new StringBuilder();
        builder.append("========================================\n");
        builder.append("字幕清洗实验台报告\n");
        builder.append("========================================\n");
        builder.append("标题: ").append(trace.getOriginalDocument().getMetadata().getOrDefault("title", "未知")).append('\n');
        builder.append("BV: ").append(trace.getOriginalDocument().getMetadata().getOrDefault("bvid", "未知")).append('\n');
        builder.append("原始行数: ").append(trace.getOriginalSegments().size()).append('\n');
        builder.append("保留行数: ").append(trace.getKeptSegments().size()).append('\n');
        builder.append("删除行数: ").append(trace.getDroppedSegmentCount()).append('\n');
        builder.append('\n');

        builder.append("----------- 原始 Transcript -----------\n");
        appendNumberedLines(builder, trace.getOriginalSegments());
        builder.append('\n');

        builder.append("----------- 决策过程 -----------\n");
        for (CleaningTrace.Decision decision : trace.getDecisions()) {
            builder.append(String.format("第 %03d 行 [%s] %s%n",
                    decision.getLineNumber(),
                    decision.getAction().name(),
                    decision.getSegment()));
            for (String reason : decision.getReasons()) {
                builder.append("  - ").append(reason).append('\n');
            }
        }
        builder.append('\n');

        builder.append("----------- 清洗后 Transcript -----------\n");
        appendNumberedLines(builder, trace.getKeptSegments());
        builder.append('\n');

        builder.append("----------- 清洗后 Document 文本 -----------\n");
        builder.append(trace.getCleanedDocument().getText()).append('\n');
        return builder.toString();
    }

    private void appendNumberedLines(StringBuilder builder, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            builder.append(String.format("%03d | %s%n", i + 1, lines.get(i)));
        }
    }
}
