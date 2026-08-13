ALTER TABLE `chunk`
    ADD COLUMN `cid` BIGINT NULL COMMENT 'B站分P CID' AFTER `chunk_text`,
    ADD COLUMN `page_number` INT NULL COMMENT '视频分P序号' AFTER `cid`,
    ADD COLUMN `start_time_ms` BIGINT NULL COMMENT '分片起始时间（毫秒）' AFTER `page_number`,
    ADD COLUMN `end_time_ms` BIGINT NULL COMMENT '分片结束时间（毫秒）' AFTER `start_time_ms`,
    ADD COLUMN `subtitle_language` VARCHAR(32) NULL COMMENT '字幕轨语言' AFTER `end_time_ms`;
