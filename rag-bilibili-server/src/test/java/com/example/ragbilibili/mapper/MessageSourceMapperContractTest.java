package com.example.ragbilibili.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSourceMapperContractTest {

    @Test
    void migrationDefinesMessageSourceKeys() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V3__add_message_source.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `message_source`");
        assertThat(sql).contains("UNIQUE KEY `uk_message_citation` (`message_id`, `citation_index`)");
        assertThat(sql).contains("KEY `idx_message_id` (`message_id`)");
    }
}
