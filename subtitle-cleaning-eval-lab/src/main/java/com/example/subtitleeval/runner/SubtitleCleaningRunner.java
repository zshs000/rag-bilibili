package com.example.subtitleeval.runner;

import com.example.subtitleeval.cleaning.CleaningTrace;
import com.example.subtitleeval.cleaning.SubtitleCleaner;
import com.example.subtitleeval.cleaning.SubtitleCleaningProperties;
import com.example.subtitleeval.model.EvalDocument;
import com.example.subtitleeval.reader.BilibiliCredentials;
import com.example.subtitleeval.reader.BilibiliDocumentReader;
import com.example.subtitleeval.reader.BilibiliResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubtitleCleaningRunner {
    public static void main(String[] args) throws Exception {
        Map<String, String> arguments = parseArgs(args);
        String target = require(arguments, "url", "必须提供 --url=<BV 或视频链接>");
        String sessdata = value(arguments, "sessdata", "BILIBILI_SESSDATA");
        String biliJct = value(arguments, "bili-jct", "BILIBILI_BILI_JCT");
        String buvid3 = value(arguments, "buvid3", "BILIBILI_BUVID3");

        BilibiliCredentials credentials = BilibiliCredentials.builder()
                .sessdata(sessdata)
                .biliJct(biliJct)
                .buvid3(buvid3)
                .build();

        BilibiliResource resource = new BilibiliResource(target, credentials);
        Path outputDir = resolveOutputDir(arguments, resource.getBvid());
        Files.createDirectories(outputDir);
        System.setProperty("subtitleeval.reader.dumpDir", outputDir.toAbsolutePath().toString());

        BilibiliDocumentReader reader = new BilibiliDocumentReader(resource);
        List<EvalDocument> documents = reader.get();
        if (documents.isEmpty()) {
            throw new IllegalStateException("没有抓到任何字幕文档，请检查凭证、视频是否存在字幕或链接是否正确。若要排查阶段性失败，请直接查看输出目录中的 player-response / subtitle-node / player-url 证据文件: "
                    + outputDir.toAbsolutePath());
        }

        EvalDocument originalDocument = documents.get(0);
        SubtitleCleaner cleaner = new SubtitleCleaner(new SubtitleCleaningProperties());
        CleaningTrace trace = cleaner.inspect(originalDocument);

        ConsoleReportFormatter formatter = new ConsoleReportFormatter();
        String report = formatter.format(originalDocument, trace);
        System.out.println(report);

        Path rawTranscriptPath = outputDir.resolve("raw-transcript.txt");
        Path cleanedTranscriptPath = outputDir.resolve("cleaned-transcript.txt");
        Path originalDocumentPath = outputDir.resolve("original-document.txt");
        Path cleanedDocumentPath = outputDir.resolve("cleaned-document.txt");
        Path reportPath = resolveReportPath(arguments, outputDir);

        Files.writeString(rawTranscriptPath, joinLines(trace.getOriginalSegments()), StandardCharsets.UTF_8);
        Files.writeString(cleanedTranscriptPath, joinLines(trace.getKeptSegments()), StandardCharsets.UTF_8);
        Files.writeString(originalDocumentPath, originalDocument.getText(), StandardCharsets.UTF_8);
        Files.writeString(cleanedDocumentPath, trace.getCleanedDocument().getText(), StandardCharsets.UTF_8);
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);

        System.out.println("原始字幕已写入: " + rawTranscriptPath.toAbsolutePath());
        System.out.println("清洗后字幕已写入: " + cleanedTranscriptPath.toAbsolutePath());
        System.out.println("原始 Document 已写入: " + originalDocumentPath.toAbsolutePath());
        System.out.println("清洗后 Document 已写入: " + cleanedDocumentPath.toAbsolutePath());
        System.out.println("清洗报告已写入: " + reportPath.toAbsolutePath());
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> arguments = new HashMap<>();
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            int splitIndex = arg.indexOf('=');
            if (splitIndex < 0) {
                arguments.put(arg.substring(2), "true");
            } else {
                arguments.put(arg.substring(2, splitIndex), arg.substring(splitIndex + 1));
            }
        }
        return arguments;
    }

    private static String require(Map<String, String> arguments, String key, String message) {
        String value = arguments.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String value(Map<String, String> arguments, String key, String envKey) {
        String argumentValue = arguments.get(key);
        if (argumentValue != null && !argumentValue.isBlank()) {
            return argumentValue;
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        throw new IllegalArgumentException("缺少参数 --" + key + "，且环境变量 " + envKey + " 也未设置。");
    }

    private static Path resolveOutputDir(Map<String, String> arguments, String bvid) {
        String outputDirArg = arguments.get("output-dir");
        if (outputDirArg != null && !outputDirArg.isBlank()) {
            return Paths.get(outputDirArg);
        }
        return Paths.get("outputs", bvid);
    }

    private static Path resolveReportPath(Map<String, String> arguments, Path outputDir) {
        String outputPath = arguments.get("output");
        if (outputPath != null && !outputPath.isBlank()) {
            Path explicitPath = Paths.get(outputPath);
            if (explicitPath.isAbsolute()) {
                return explicitPath;
            }
            if (explicitPath.getParent() != null) {
                return explicitPath;
            }
            return outputDir.resolve(explicitPath);
        }
        return outputDir.resolve("report.txt");
    }

    private static String joinLines(List<String> lines) {
        return String.join(System.lineSeparator(), lines);
    }
}
