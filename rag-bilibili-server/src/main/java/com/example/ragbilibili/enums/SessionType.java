package com.example.ragbilibili.enums;

/**
 * 会话类型枚举
 */
public enum SessionType {
    /**
     * 单视频对话
     */
    SINGLE_VIDEO("SINGLE_VIDEO", "单视频对话"),

    /**
     * 全部视频对话
     */
    ALL_VIDEOS("ALL_VIDEOS", "全部视频对话");

    private final String code;
    private final String description;

    SessionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
