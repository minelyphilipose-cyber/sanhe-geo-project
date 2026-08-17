package com.huanjing.geo.module.retention.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.EvidenceDocument;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionAuditJsonCodecTest {

    private final DataRetentionAuditJsonCodec codec = new DataRetentionAuditJsonCodec(new ObjectMapper());

    @Test
    void acceptsOpaqueEvidenceAndPreservesDigestIdentity() {
        byte[] bytes = "{\"result\":\"committed\", \"count\":1}".getBytes(StandardCharsets.UTF_8);
        var validated = codec.validateEvidence(new EvidenceDocument(bytes,
                DataRetentionCanonicalEncoder.sha256(bytes)));
        assertEquals(32, validated.sha256Digest().length);
        assertEquals("{\"result\":\"committed\",\"count\":1}", validated.diagnosticJson());
    }

    @Test
    void rejectsDigestMismatchBomDuplicateKeysAndRecursiveSecrets() {
        assertEvidenceInvalid("{\"result\":1}", new byte[32]);
        byte[] bom = new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'};
        assertThrows(DataRetentionAuditException.class,
                () -> codec.validateEvidence(new EvidenceDocument(bom, DataRetentionCanonicalEncoder.sha256(bom))));
        assertEvidenceInvalid("{\"a\":1,\"a\":2}", null);
        assertEvidenceInvalid("{\"outer\":{\"Authorization\":\"x\"}}", null);
    }

    @Test
    void enforcesRawUtf8ByteBoundaryAndTruncatesByCodePoint() {
        String prefix = "{\"v\":\"";
        String suffix = "\"}";
        String accepted = prefix + "a".repeat(8192 - prefix.length() - suffix.length()) + suffix;
        byte[] acceptedBytes = accepted.getBytes(StandardCharsets.UTF_8);
        assertEquals(8192, acceptedBytes.length);
        codec.validateEvidence(new EvidenceDocument(acceptedBytes,
                DataRetentionCanonicalEncoder.sha256(acceptedBytes)));

        byte[] rejected = (prefix + "a".repeat(8193 - prefix.length() - suffix.length()) + suffix)
                .getBytes(StandardCharsets.UTF_8);
        DataRetentionAuditException exception = assertThrows(DataRetentionAuditException.class,
                () -> codec.validateEvidence(new EvidenceDocument(rejected,
                        DataRetentionCanonicalEncoder.sha256(rejected))));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID, exception.getErrorCode());
        assertEquals("中", codec.truncateUtf8("中🙂", 3));
        assertTrue(codec.truncateUtf8("中🙂", 7).endsWith("🙂"));
    }

    @Test
    void generatedAuditJsonRejectsSensitiveKeysPojoAndObjectArrays() {
        assertGeneratedInvalid(Map.of("Authorization", "secret"));
        assertGeneratedInvalid(Map.of("items", List.of(Map.of("id", 1))));
        assertGeneratedInvalid(Map.of("value", new Object()));
        assertEquals("{\"count\":1,\"flags\":[true,false]}",
                codec.writeStableObject(Map.of("flags", List.of(true, false), "count", 1), false));
    }

    private void assertEvidenceInvalid(String json, byte[] digest) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] actualDigest = digest == null ? DataRetentionCanonicalEncoder.sha256(bytes) : digest;
        DataRetentionAuditException exception = assertThrows(DataRetentionAuditException.class,
                () -> codec.validateEvidence(new EvidenceDocument(bytes, actualDigest)));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID, exception.getErrorCode());
    }

    private void assertGeneratedInvalid(Map<String, Object> value) {
        DataRetentionAuditException exception = assertThrows(DataRetentionAuditException.class,
                () -> codec.writeStableObject(value, false));
        assertEquals(DataRetentionAuditErrors.CONTEXT_INVALID, exception.getErrorCode());
    }
}
