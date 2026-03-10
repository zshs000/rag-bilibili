package com.example.ragbilibili.enums;

/**
 * 消息角色枚举
 */
public enum MessageRole {
    /**
     * 用户消息
     */
    USER("USER", "用户"),

    /**
     * 助手消息
     */
    ASSISTANT("ASSISTANT", "助手");

    private final String code;
    private final String description;

    MessageRole(String code, String description) {
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
