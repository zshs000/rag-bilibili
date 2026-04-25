package com.example.ragbilibili.probe;

public class SubtitleProbeResult {
    private final SubtitleProbeStatus status;
    private final String reason;

    public SubtitleProbeResult(SubtitleProbeStatus status, String reason) {
        this.status = status == null ? SubtitleProbeStatus.UNKNOWN : status;
        this.reason = reason == null ? "" : reason;
    }

    public SubtitleProbeStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public boolean hasSubtitleButton() {
        return status == SubtitleProbeStatus.HAS_SUBTITLE_BUTTON;
    }

    public boolean hasNoSubtitleButton() {
        return status == SubtitleProbeStatus.NO_SUBTITLE_BUTTON;
    }

    public static SubtitleProbeResult hasSubtitleButton(String reason) {
        return new SubtitleProbeResult(SubtitleProbeStatus.HAS_SUBTITLE_BUTTON, reason);
    }

    public static SubtitleProbeResult noSubtitleButton(String reason) {
        return new SubtitleProbeResult(SubtitleProbeStatus.NO_SUBTITLE_BUTTON, reason);
    }

    public static SubtitleProbeResult unknown(String reason) {
        return new SubtitleProbeResult(SubtitleProbeStatus.UNKNOWN, reason);
    }
}
