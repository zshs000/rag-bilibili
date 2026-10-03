package com.example.ragbilibili.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VideoImportBatchMigrationTest {

    @Test
    void activeBvidGeneratedColumnBelongsToImportItemTable() throws IOException {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/migration/V4__add_video_import_batch.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int itemTableStart = sql.indexOf("CREATE TABLE IF NOT EXISTS `video_import_item`");
        assertThat(itemTableStart).isPositive();

        String batchTable = sql.substring(0, itemTableStart);
        String itemTable = sql.substring(itemTableStart);
        assertThat(batchTable).doesNotContain("`active_bvid`");
        assertThat(itemTable)
                .contains("`active_bvid`")
                .contains("UNIQUE KEY `uk_item_user_active_bvid` (`user_id`, `active_bvid`)");
    }
}
