package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataRetentionDetailCleanupConfigurationTest {
    @Test
    void javaDefaultsRemainBoundedAndDisabled() {
        DataRetentionProperties.DetailCleanup value = new DataRetentionProperties().getDetailCleanup();
        assertThat(value.isEnabled()).isFalse();
        assertThat(value.getMaxOperationsPerRun()).isEqualTo(20);
        assertThat(value.getMaxOperationlessRunsPerRun()).isEqualTo(20);
        assertThat(value.getMaxAttemptsPerOperation()).isEqualTo(1000);
        assertThat(value.getMaxOperationlessAttemptsPerRun()).isEqualTo(1000);
        assertThat(value.getMaxRunSeconds()).isEqualTo(3600);
        assertThat(value.getLockKey()).isEqualTo("geo:retention:detail-cleanup:lock");
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDefaultsKeepEveryWriteGateDisabled() {
        try (InputStream stream = getClass().getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(stream);
            Map<String, Object> retention = (Map<String, Object>) ((Map<String, Object>) root.get("geo")).get("retention");
            assertThat(retention.get("detail-cleanup").toString()).contains("GEO_RETENTION_DETAIL_CLEANUP_ENABLED:false");
            assertThat(retention.get("dynamic-switch").toString()).contains("GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false");
            assertThat(retention.get("scheduler").toString()).contains("GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED:false");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
