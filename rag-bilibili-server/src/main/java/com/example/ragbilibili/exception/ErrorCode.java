package com.example.ragbilibili.exception;

/**
 * 错误码枚举
 */
public enum ErrorCode {
    // 通用错误
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统错误"),
    PARAM_ERROR(400, "参数错误"),

    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    NOT_LOGGED_IN(1004, "未登录"),

    // 视频相关
    VIDEO_NOT_FOUND(2001, "视频不存在"),
    VIDEO_ALREADY_EXISTS(2002, "视频已存在"),
    VIDEO_IMPORT_FAILED(2003, "视频导入失败"),
    VIDEO_NO_SUBTITLE(2004, "视频无字幕"),
    BVID_PARSE_ERROR(2005, "BV号解析失败"),

    // 会话相关
    SESSION_NOT_FOUND(3001, "会话不存在"),
    SESSION_TYPE_ERROR(3002, "会话类型错误"),

    // 向量相关
    VECTOR_DELETE_FAILED(4001, "向量删除失败"),
    VECTOR_WRITE_FAILED(4002, "向量写入失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
