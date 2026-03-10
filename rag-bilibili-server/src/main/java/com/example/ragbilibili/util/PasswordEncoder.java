package com.example.ragbilibili.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码加密工具类
 * 使用 jBCrypt 库实现 BCrypt 加密
 * 依赖：org.mindrot:jbcrypt:0.4
 */
public class PasswordEncoder {
    /**
     * 加密密码
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 验证密码
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
