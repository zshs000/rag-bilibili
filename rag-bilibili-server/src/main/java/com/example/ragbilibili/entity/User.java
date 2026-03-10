package com.example.ragbilibili.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
public class User {
    /**
     * 用户主键
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
