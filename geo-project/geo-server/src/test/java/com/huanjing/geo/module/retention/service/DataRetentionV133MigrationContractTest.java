package com.huanjing.geo.module.retention.service;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionV133MigrationContractTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration")
            .toAbsolutePath();
    private static final Path CANDIDATE = Path.of(
            "src/test/resources/retention/v133-candidate/"
                    + "V132_1__prepare_article_batch_channel_slot_unique.sql")
            .toAbsolutePath();
    private static final String EXISTING_MANIFEST_SHA256 =
            "3fec5b51579ef88931300417cec353501d6295f019963169fc032328e8cc59ca";
    private static final String V133_SHA256 =
            "f41035f934d51d1d08eae593b5320ba02588841f2523818c4911bcb254ed3ce2";

    @Test
    void freezesExistingMigrationHistoryAndV133Bytes() throws Exception {
        List<Path> migrations;
        try (var files = Files.list(MIGRATION_DIRECTORY)) {
            migrations = files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertEquals(371, migrations.size());
        StringBuilder manifest = new StringBuilder();
        for (Path migration : migrations) {
            byte[] canonicalBytes = canonicalMigrationBytes(Files.readAllBytes(migration));
            manifest.append(migration.getFileName())
                    .append('|').append(canonicalBytes.length)
                    .append('|').append(sha256(canonicalBytes))
                    .append('\n');
        }
        assertEquals(EXISTING_MANIFEST_SHA256,
                sha256(manifest.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(V133_SHA256, sha256(canonicalMigrationBytes(Files.readAllBytes(
                MIGRATION_DIRECTORY.resolve("V133__article_batch_channel_slot_unique.sql")))));
    }

    @Test
    void canonicalMigrationBytesIgnoreCheckoutLineEndingsAndPreserveBom() throws Exception {
        byte[] lf = "\uFEFFSELECT 1;\nSELECT 2;\n".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "\uFEFFSELECT 1;\r\nSELECT 2;\r\n".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(lf, canonicalMigrationBytes(crlf));
        assertEquals(sha256(lf), sha256(canonicalMigrationBytes(crlf)));
    }

    @Test
    void candidateIsStrictlyBeforeV133AndContainsOnlyMysqlSchemaWork() throws Exception {
        assertTrue(Files.isRegularFile(CANDIDATE));
        MigrationVersion candidate = MigrationVersion.fromVersion("132.1");
        assertTrue(candidate.compareTo(MigrationVersion.fromVersion("132")) > 0);
        assertTrue(candidate.compareTo(MigrationVersion.fromVersion("133")) < 0);

        String sql = Files.readString(CANDIDATE);
        assertTrue(sql.contains("0:project_id,batch_date,target_channel,generation_slot_no"));
        assertTrue(sql.contains("ADD UNIQUE KEY uk_article_batch_project_date_channel_slot "
                + "(project_id, batch_date, target_channel, generation_slot_no)"));
        assertTrue(sql.contains("SIGNAL SQLSTATE ''45000''"));
        assertFalse(sql.toLowerCase().contains("redis"));
        assertFalse(sql.toLowerCase().contains("data_retention_dynamic_switch"));
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private byte[] canonicalMigrationBytes(byte[] bytes) throws CharacterCodingException {
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .getBytes(StandardCharsets.UTF_8);
    }
}
