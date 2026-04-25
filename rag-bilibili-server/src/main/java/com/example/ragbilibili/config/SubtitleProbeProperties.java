package com.example.ragbilibili.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.subtitle-probe")
public class SubtitleProbeProperties {
    private boolean enabled = true;
    private String nodeCommand = "node";
    private String scriptPath = "../subtitle-probe/probe.mjs";
    private long timeoutMillis = 10000;
    private long[] retryDelaysMillis = new long[]{1000, 2500, 5000};

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNodeCommand() {
        return nodeCommand;
    }

    public void setNodeCommand(String nodeCommand) {
        this.nodeCommand = nodeCommand;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public long getTimeoutMillis() {
        return Math.max(1000, timeoutMillis);
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long[] getRetryDelaysMillis() {
        return retryDelaysMillis == null || retryDelaysMillis.length == 0
                ? new long[]{1000, 2500, 5000}
                : retryDelaysMillis.clone();
    }

    public void setRetryDelaysMillis(long[] retryDelaysMillis) {
        this.retryDelaysMillis = retryDelaysMillis == null ? null : retryDelaysMillis.clone();
    }
}
