package com.example.ragbilibili.enums;

/**
 * 视频状态枚举
 */
public enum VideoStatus {
    /**
     * 导入中
     */
    IMPORTING("IMPORTING", "导入中"),

    /**
     * 导入成功
     */
    SUCCESS("SUCCESS", "导入成功"),

    /**
     * 导入失败
     */
    FAILED("FAILED", "导入失败");

    private final String code;
    private final String description;

    VideoStatus(String code, String description) {
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
