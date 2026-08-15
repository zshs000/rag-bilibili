CREATE TABLE IF NOT EXISTS `video_import_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `status` VARCHAR(30) NOT NULL COMMENT 'RUNNING/COMPLETED/PARTIAL_FAILED',
    `total_count` INT NOT NULL DEFAULT 0,
    `queued_count` INT NOT NULL DEFAULT 0,
    `running_count` INT NOT NULL DEFAULT 0,
    `succeeded_count` INT NOT NULL DEFAULT 0,
    `skipped_count` INT NOT NULL DEFAULT 0,
    `failed_count` INT NOT NULL DEFAULT 0,
    `credentials_ciphertext` TEXT COMMENT 'AES-GCM 加密的临时导入凭证',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_batch_user_created` (`user_id`, `create_time`),
    KEY `idx_batch_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频批量导入批次';

CREATE TABLE IF NOT EXISTS `video_import_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    `batch_id` BIGINT NOT NULL COMMENT '批次ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `original_input` VARCHAR(500) NOT NULL COMMENT '用户原始输入',
    `bvid` VARCHAR(50) NULL COMMENT '规范化BV号，解析失败时为空',
    `status` VARCHAR(20) NOT NULL COMMENT 'QUEUED/RUNNING/SUCCEEDED/SKIPPED/FAILED',
    `fail_reason` VARCHAR(500) NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `video_id` BIGINT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `start_time` DATETIME NULL,
    `finish_time` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `idx_item_batch_id` (`batch_id`, `id`),
    KEY `idx_item_status_id` (`status`, `id`),
    KEY `idx_item_user_bvid` (`user_id`, `bvid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频批量导入明细';
