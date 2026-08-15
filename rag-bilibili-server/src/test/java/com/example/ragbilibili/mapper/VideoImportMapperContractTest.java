package com.example.ragbilibili.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VideoImportMapperContractTest {

    @Test
    void migrationDefinesBatchAndItemTables() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V4__add_video_import_batch.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `video_import_batch`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `video_import_item`");
        assertThat(sql).contains("`credentials_ciphertext` TEXT");
        assertThat(sql).contains("KEY `idx_batch_user_created` (`user_id`, `create_time`)");
        assertThat(sql).contains("KEY `idx_item_status_id` (`status`, `id`)");
    }

    @Test
    void mapperSqlEnforcesOwnershipAndConditionalClaiming() throws IOException {
        String batchXml = Files.readString(Path.of(
                "src/main/resources/mapper/VideoImportBatchMapper.xml"));
        String itemXml = Files.readString(Path.of(
                "src/main/resources/mapper/VideoImportItemMapper.xml"));

        assertThat(batchXml).contains("WHERE id = #{id} AND user_id = #{userId}");
        assertThat(batchXml).contains("WHERE user_id = #{userId}");
        assertThat(batchXml).contains("LIMIT 20");
        assertThat(itemXml).contains("WHERE id = #{id} AND status = 'QUEUED'");
        assertThat(itemXml).contains("status IN ('QUEUED', 'RUNNING')");
        assertThat(itemXml).contains("WHERE batch_id = #{batchId} AND status = 'FAILED'");
        assertThat(itemXml).contains("SET status = 'QUEUED', start_time = NULL");
    }
}
