package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionExecuteGovernanceMigrationContractTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");
    private static final Path MIGRATION = MIGRATION_DIRECTORY.resolve(
            "V354__data_retention_execute_governance.sql");
    private static final Path SPEC = Path.of(
            "../docs/data-retention-p0-wp2-ddl-spec-2026-08-11.md");
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void reservesUniqueV354MigrationWithinTheMigrationSequence() throws Exception {
        List<Integer> versions;
        try (var files = Files.list(MIGRATION_DIRECTORY)) {
            versions = files
                    .map(path -> path.getFileName().toString())
                    .map(VERSION::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted()
                    .toList();
        }

        assertTrue(Files.isRegularFile(MIGRATION));
        assertEquals(versions.size(), versions.stream().distinct().count());
        assertTrue(versions.get(versions.size() - 1) >= 354);
        assertEquals(1, versions.stream().filter(version -> version == 354).count());
    }

    @Test
    void createsTheFrozenGovernanceModelWithoutEnablingExecution() throws Exception {
        String sql = Files.readString(MIGRATION);

        for (String table : List.of(
                "data_retention_operation",
                "data_retention_operation_run",
                "data_retention_batch_attempt",
                "data_retention_batch_item",
                "data_retention_batch_commit",
                "data_retention_failure_group",
                "data_retention_failure_group_item",
                "data_retention_failure_active_key",
                "data_retention_failure_regroup_history",
                "data_retention_dynamic_switch_change",
                "data_retention_unknown_outcome_reconciliation")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table + " ("), table);
        }

        assertFalse(sql.contains("data_retention_run_group"));
        assertFalse(sql.toUpperCase().contains("ON DELETE CASCADE"));
        assertFalse(sql.matches("(?s).*execute[^\\n=]*=\\s*(?:1|true).*"));
        assertTrue(sql.contains("SIGNAL SQLSTATE ''45000''"));
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertTrue(sql.contains("information_schema.referential_constraints"));
        assertTrue(sql.contains("information_schema.table_constraints"));
        assertTrue(sql.contains("information_schema.check_constraints"));
        assertContainsAll(sql, List.of(
                "@v354_table_signature",
                "@v354_column_signature",
                "@v354_index_signature",
                "@v354_fk_signature",
                "@v354_check_signature",
                "V354_exact_governance_schema_signature_mismatch",
                "620635fcbf3b38e0eab257dfc218e7ff76b04790096f77ee72421ab868e47d3a",
                "a24bbe48cbc4f73e0b39cdb2cd511ba8d212080afc5542c02ddf728ac9b923e4",
                "cd4e2ebb1d72e5954a55435248252893ffe8db202e8a5d0461860d48e080eacf",
                "4422b9669b8eb34bc5d596d6b933bca51d83e84fa965873fc7c01ad568c60d3c",
                "12eb71af38a45506be1cb1a4a2b60ef76aead565c0860a921c8b575c236064a6"));
        int checkSignatureStart = sql.indexOf("SET @v354_check_signature := (");
        int checkSignatureEnd = sql.indexOf("SET @v354_sql := IF(", checkSignatureStart);
        String checkSignatureSql = sql.substring(checkSignatureStart, checkSignatureEnd);
        assertFalse(checkSignatureSql.contains("LOWER("));
        assertTrue(checkSignatureSql.contains("cc.check_clause"));
        assertFalse(checkSignatureSql.contains("REGEXP_REPLACE("));
        assertFalse(checkSignatureSql.contains("REPLACE(cc.check_clause"));
        assertEquals(occurrences(sql, "\nPREPARE v354_"),
                occurrences(sql, "\nDEALLOCATE PREPARE v354_"));
    }

    @Test
    void freezesOperationLifecycleAndCrossRunContracts() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertContainsAll(sql, List.of(
                "origin_run_group_id BIGINT UNSIGNED NOT NULL",
                "batch_key_hash BINARY(32) NOT NULL",
                "config_fingerprint_schema_version SMALLINT UNSIGNED NOT NULL",
                "created_application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL",
                "committed_application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL",
                "detail_retention_anchor_at DATETIME(3) NULL",
                "detail_purged_at DATETIME(3) NULL",
                "detail_purged_at >= TIMESTAMPADD(DAY, 365, detail_retention_anchor_at)",
                "config_fingerprint IS NOT NULL AND config_fingerprint_schema_version IS NOT NULL AND config_fingerprint_schema_version >= 1",
                "uk_retention_operation_execution_contract",
                "uk_retention_operation_website_contract",
                "uk_retention_operation_poll_scope",
                "uk_retention_operation_run_origin_once",
                "uk_retention_operation_run_identity",
                "fk_retention_operation_run_operation_contract",
                "fk_retention_operation_run_run_contract"));

        assertTrue(sql.contains("CASE WHEN relation_type = 'origin' THEN operation_id ELSE NULL END"));
        assertTrue(sql.contains("relation_type IN ('origin','retry','reconcile')"));
        assertFalse(sql.contains("FOREIGN KEY (parent_attempt_id)"));
        int commitStart = sql.indexOf("CREATE TABLE IF NOT EXISTS data_retention_batch_commit (");
        int commitEnd = sql.indexOf(") ENGINE=InnoDB", commitStart);
        String commitDdl = sql.substring(commitStart, commitEnd);
        assertFalse(commitDdl.matches("(?s).*FOREIGN KEY.*application_version.*"));
    }

    @Test
    void freezesAttemptItemMarkerAndPollRelationships() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertContainsAll(sql, List.of(
                "CHECK (sequence_no >= 1)",
                "CHECK (attempt_no >= 1)",
                "uk_retention_attempt_operation_id",
                "idx_retention_batch_item_attempt",
                "fk_retention_batch_item_attempt",
                "uk_retention_batch_commit_scope",
                "idx_retention_batch_commit_contract",
                "uk_retention_purged_slice_operation",
                "idx_retention_purged_slice_operation_run",
                "idx_retention_purged_slice_operation_scope",
                "fk_retention_purged_slice_operation_run",
                "fk_retention_purged_slice_operation_scope",
                "idx_poll_audit_purge_retention_run",
                "idx_poll_audit_purge_operation",
                "fk_poll_audit_purge_operation",
                "retention_run_id IS NULL AND operation_id IS NULL AND retention_domain IS NULL",
                "retention_run_id IS NOT NULL AND operation_id IS NOT NULL AND retention_domain IS NOT NULL"));

        assertTrue(sql.contains(
                "poll_question_tier VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL"));
        assertTrue(sql.contains(
                "retention_domain VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL"));
        assertFalse(sql.contains("batch_attempt_id"));
    }

    @Test
    void freezesFailureAndAuditKeysAndSizeLimits() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertContainsAll(sql, List.of(
                "uk_retention_failure_group_id_domain",
                "uk_retention_failure_item_key",
                "uk_retention_failure_item_sequence",
                "uk_retention_failure_item_active_ref",
                "PRIMARY KEY (domain, business_key)",
                "idx_retention_failure_active_ref",
                "fk_retention_failure_active_ref",
                "old_group_id BIGINT UNSIGNED NOT NULL",
                "new_group_id BIGINT UNSIGNED NOT NULL",
                "CHECK (old_group_id <> new_group_id)",
                "uk_retention_switch_version",
                "uk_retention_reconciliation_id",
                "OCTET_LENGTH(CAST(scope_json AS CHAR)) <= 8192",
                "OCTET_LENGTH(CAST(metrics_json AS CHAR)) <= 8192",
                "OCTET_LENGTH(CAST(evidence_json AS CHAR)) <= 8192",
                "OCTET_LENGTH(last_error) <= 2048"));
    }

    @Test
    void keepsCanonicalFingerprintAndScopeVectorsReproducible() throws Exception {
        String websiteFingerprint = """
                batchMaxItems=20
                batchMinItems=5
                businessTimezone=Asia/Shanghai
                postCheckMode=locked_rows_only
                requireBodyPurged=true
                requireLightweightEvidencePreserved=true
                requireMarkerPresent=true
                requirePayloadPurged=true
                requireRawResponsePurged=true
                retentionHours=24
                splitStrategy=halve_to_min_5
                """;
        String pollFingerprint = """
                businessTimezone=Asia/Shanghai
                hotRetentionDays=14
                lateWritePolicy=http_409
                preserveLatest=true
                requireAllChildrenPurged=true
                requireMarkerPresent=true
                requireSummaryConsistent=true
                sliceTransactionMode=single_slice
                stuckBatchSealDays=7
                """;
        String websiteScope = """
                hashSchemaVersion=1
                domain=website_published_content
                businessKeyType=article_id
                itemCount=5
                businessKey[0]=101
                businessKey[1]=205
                businessKey[2]=309
                businessKey[3]=410
                businessKey[4]=512
                cutoff=2026-08-10T03:30:00.000Z
                upperBound=512
                policyVersion=website-published-v1
                configFingerprintSchemaVersion=1
                configFingerprint=5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd
                """;
        String pollScope = """
                hashSchemaVersion=1
                domain=poll_results
                projectId=1
                batchDate=2026-05-26
                questionTier=A
                cutoff=2026-07-28
                upperBound=<null>
                policyVersion=poll-results-v1
                configFingerprintSchemaVersion=1
                configFingerprint=a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c
                """;

        assertEquals("5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd",
                sha256(websiteFingerprint));
        assertEquals("a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c",
                sha256(pollFingerprint));
        assertEquals("14a67e69ebea79dea3c01abef4cd982c8fcb22303ec519941e7f319c633abbe7",
                sha256(websiteScope));
        assertEquals("2d6d6c4ccf5609e29a14773efc97125dcd7746e192be7bc1f163b274c06eedad",
                sha256(pollScope));

        String spec = Files.readString(SPEC);
        assertFalse(spec.contains("configFingerprint=<64-lowercase-hex>"));
        assertTrue(spec.contains("operation → reconciliation/failure gate → attempt → batch item"));
        assertTrue(spec.contains("按 batch item → attempt 的 FK 安全顺序删除"));
    }

    private void assertContainsAll(String value, List<String> tokens) {
        for (String token : tokens) {
            assertTrue(value.contains(token), token);
        }
    }

    private int occurrences(String value, String token) {
        return value.split(Pattern.quote(token), -1).length - 1;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
