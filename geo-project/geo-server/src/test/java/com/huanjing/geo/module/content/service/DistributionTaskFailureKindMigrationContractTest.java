package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionTaskFailureKindMigrationContractTest {

    @Test
    void expandsFailureKindForStructuredWechatErrors() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V372__expand_distribution_task_failure_kind.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).containsIgnoringCase("ALTER TABLE distribution_tasks")
                    .containsIgnoringCase("MODIFY COLUMN failure_kind VARCHAR(64)");
        }
    }
}
