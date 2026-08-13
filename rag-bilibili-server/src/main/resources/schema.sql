-- 创建数据库
CREATE DATABASE IF NOT EXISTS rag_bilibili DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE rag_bilibili;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 视频表
CREATE TABLE IF NOT EXISTS `video` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '视频主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `bvid` VARCHAR(50) NOT NULL COMMENT 'BV号',
    `title` VARCHAR(255) NOT NULL COMMENT '视频标题',
    `description` TEXT COMMENT '视频简介',
    `import_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'IMPORTING' COMMENT '状态：IMPORTING/SUCCESS/FAILED',
    `fail_reason` VARCHAR(500) COMMENT '导入失败原因',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_bvid` (`user_id`, `bvid`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频表';

-- 分片表
CREATE TABLE IF NOT EXISTS `chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分片主键',
    `video_id` BIGINT NOT NULL COMMENT '视频ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `bvid` VARCHAR(50) NOT NULL COMMENT 'BV号（冗余存储）',
    `title` VARCHAR(255) NOT NULL COMMENT '视频标题（冗余存储）',
    `chunk_index` INT NOT NULL COMMENT '分片序号',
    `total_chunks` INT NOT NULL COMMENT '总分片数（冗余存储）',
    `chunk_text` TEXT NOT NULL COMMENT '分片文本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_video_id` (`video_id`),
    KEY `idx_bvid` (`bvid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分片表';

-- 向量映射表
CREATE TABLE IF NOT EXISTS `vector_mapping` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '映射主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `video_id` BIGINT NOT NULL COMMENT '视频ID',
    `chunk_id` BIGINT NOT NULL COMMENT '分片ID',
    `vector_id` VARCHAR(255) NOT NULL COMMENT '向量ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vector_id` (`vector_id`),
    KEY `idx_video_id` (`video_id`),
    KEY `idx_chunk_id` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量映射表';

-- 会话表
CREATE TABLE IF NOT EXISTS `session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_type` VARCHAR(20) NOT NULL COMMENT '会话类型：SINGLE_VIDEO/ALL_VIDEOS',
    `video_id` BIGINT COMMENT '关联视频ID（单视频对话时有值）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_video_id` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色类型：USER/ASSISTANT',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';
