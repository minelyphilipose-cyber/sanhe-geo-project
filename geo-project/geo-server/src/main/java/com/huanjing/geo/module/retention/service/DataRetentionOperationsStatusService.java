package com.huanjing.geo.module.retention.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataRetentionOperationsStatusService {

    static final List<String> REQUIRED_FORWARD_VERSIONS = List.of("365", "366", "367", "368");
    private static final String REQUIRED_VERSION = "368";

    private final JdbcTemplate jdbc;

    public SchemaStatus schemaStatus() {
        try {
            List<String> successfulVersions = jdbc.queryForList("""
                    SELECT version
                      FROM flyway_schema_history
                     WHERE success = 1
                       AND version IS NOT NULL
                     ORDER BY installed_rank ASC
                    """, String.class);
            String currentVersion = successfulVersions.isEmpty()
                    ? null
                    : successfulVersions.get(successfulVersions.size() - 1);
            Set<String> successful = new HashSet<>(successfulVersions);
            List<String> missing = REQUIRED_FORWARD_VERSIONS.stream()
                    .filter(version -> !successful.contains(version))
                    .toList();
            return new SchemaStatus(true, currentVersion, REQUIRED_VERSION, missing,
                    missing.isEmpty(), null);
        } catch (DataAccessException error) {
            return new SchemaStatus(false, null, REQUIRED_VERSION, REQUIRED_FORWARD_VERSIONS,
                    false, "FLYWAY_HISTORY_QUERY_FAILED");
        }
    }

    public record SchemaStatus(
            boolean historyQuerySucceeded,
            String currentVersion,
            String requiredVersion,
            List<String> missingRequiredVersions,
            boolean compatibleForLocalAcceptance,
            String errorCode) {
    }
}
