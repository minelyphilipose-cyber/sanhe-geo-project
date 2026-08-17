package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.module.retention.service.DataRetentionOperationsStatusService.SchemaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataRetentionOperationsStatusServiceTest {

    @Test
    void v364HistoryIsNotCompatibleWithCurrentRetentionCode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("354", "359", "364"));

        SchemaStatus status = new DataRetentionOperationsStatusService(jdbc).schemaStatus();

        assertThat(status.historyQuerySucceeded()).isTrue();
        assertThat(status.currentVersion()).isEqualTo("364");
        assertThat(status.requiredVersion()).isEqualTo("368");
        assertThat(status.missingRequiredVersions()).containsExactly("365", "366", "367", "368");
        assertThat(status.compatibleForLocalAcceptance()).isFalse();
    }

    @Test
    void v368HistoryWithAllForwardMigrationsIsCompatible() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("354", "364", "365", "366", "367", "368"));

        SchemaStatus status = new DataRetentionOperationsStatusService(jdbc).schemaStatus();

        assertThat(status.currentVersion()).isEqualTo("368");
        assertThat(status.missingRequiredVersions()).isEmpty();
        assertThat(status.compatibleForLocalAcceptance()).isTrue();
        assertThat(status.errorCode()).isNull();
    }

    @Test
    void historyQueryFailureIsFailClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        SchemaStatus status = new DataRetentionOperationsStatusService(jdbc).schemaStatus();

        assertThat(status.historyQuerySucceeded()).isFalse();
        assertThat(status.compatibleForLocalAcceptance()).isFalse();
        assertThat(status.errorCode()).isEqualTo("FLYWAY_HISTORY_QUERY_FAILED");
    }
}
