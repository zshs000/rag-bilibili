CREATE TABLE IF NOT EXISTS `message_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息来源主键',
    `message_id` BIGINT NOT NULL COMMENT '助手消息ID',
    `citation_index` INT NOT NULL COMMENT '回答中的引用编号',
    `vector_id` VARCHAR(255) NOT NULL COMMENT '检索向量ID快照',
    `bvid` VARCHAR(50) NOT NULL COMMENT 'BV号快照',
    `video_title` VARCHAR(255) NOT NULL COMMENT '视频标题快照',
    `cid` BIGINT COMMENT '分P CID快照',
    `page_number` INT NOT NULL COMMENT '分P序号快照',
    `start_time_ms` BIGINT NOT NULL COMMENT '字幕片段开始时间（毫秒）',
    `end_time_ms` BIGINT NOT NULL COMMENT '字幕片段结束时间（毫秒）',
    `snippet` TEXT NOT NULL COMMENT '字幕片段文本快照',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_citation` (`message_id`, `citation_index`),
    KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='助手消息引用来源快照表';
