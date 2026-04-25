package com.example.ragbilibili.probe;

import com.alibaba.cloud.ai.reader.bilibili.BilibiliCredentials;
import com.example.ragbilibili.config.SubtitleProbeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PlaywrightSubtitleProbeService {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightSubtitleProbeService.class);

    private final SubtitleProbeProperties properties;
    private final ObjectMapper objectMapper;

    public PlaywrightSubtitleProbeService(SubtitleProbeProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public SubtitleProbeResult probe(String videoUrl, BilibiliCredentials credentials) {
        if (!properties.isEnabled()) {
            return SubtitleProbeResult.unknown("subtitle probe disabled");
        }
        if (videoUrl == null || videoUrl.isBlank()) {
            return SubtitleProbeResult.unknown("video url blank");
        }

        Path scriptPath = resolveScriptPath();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            return SubtitleProbeResult.unknown("probe script not found");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                properties.getNodeCommand(),
                scriptPath.toAbsolutePath().toString(),
                "--url",
                videoUrl,
                "--timeout-ms",
                String.valueOf(properties.getTimeoutMillis())
        );

        Map<String, String> environment = processBuilder.environment();
        if (credentials != null) {
            putIfHasText(environment, "BILIBILI_SESSDATA", credentials.getSessdata());
            putIfHasText(environment, "BILIBILI_BILI_JCT", credentials.getBiliJct());
            putIfHasText(environment, "BILIBILI_BUVID3", credentials.getBuvid3());
        }

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(properties.getTimeoutMillis() + 3000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return SubtitleProbeResult.unknown("probe process timeout");
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (process.exitValue() != 0) {
                log.warn("subtitle probe exited with code={}, stderr={}", process.exitValue(), stderr);
                return SubtitleProbeResult.unknown("probe process exit code=" + process.exitValue());
            }

            if (stdout.isBlank()) {
                log.warn("subtitle probe returned blank output, stderr={}", stderr);
                return SubtitleProbeResult.unknown("probe output blank");
            }

            JsonNode jsonNode = objectMapper.readTree(stdout);
            SubtitleProbeStatus status = parseStatus(jsonNode.path("status").asText(""));
            String reason = jsonNode.path("reason").asText("");
            if (status == SubtitleProbeStatus.UNKNOWN && !stderr.isBlank()) {
                log.info("subtitle probe unknown, stderr={}", stderr);
            }
            return new SubtitleProbeResult(status, reason);
        } catch (IOException e) {
            log.warn("subtitle probe process start failed: {}", e.getMessage());
            return SubtitleProbeResult.unknown("probe process start failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubtitleProbeResult.unknown("probe interrupted");
        }
    }

    private SubtitleProbeStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return SubtitleProbeStatus.UNKNOWN;
        }
        try {
            return SubtitleProbeStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException ignored) {
            return SubtitleProbeStatus.UNKNOWN;
        }
    }

    private void putIfHasText(Map<String, String> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.put(key, value);
        }
    }

    private Path resolveScriptPath() {
        Path configuredPath = Paths.get(properties.getScriptPath());
        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = new Path[]{
                cwd.resolve(configuredPath).normalize(),
                cwd.resolve("subtitle-probe/probe.mjs").normalize(),
                cwd.resolve("../subtitle-probe/probe.mjs").normalize()
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return configuredPath.normalize();
    }
}
