package com.example.ragbilibili.util;

import com.example.ragbilibili.exception.BusinessException;
import com.example.ragbilibili.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BV号解析工具类
 */
public class BVIDParser {
    /**
     * BV号正则表达式
     * 匹配格式：BV + 10位字符（数字和大小写字母）
     */
    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[a-zA-Z0-9]{10})");

    /**
     * 从输入字符串中解析 BV 号
     *
     * @param input BV号或包含BV号的URL
     * @return BV号
     * @throws BusinessException 解析失败时抛出
     */
    public static String parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BVID_PARSE_ERROR);
        }

        Matcher matcher = BVID_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new BusinessException(ErrorCode.BVID_PARSE_ERROR);
    }
}
