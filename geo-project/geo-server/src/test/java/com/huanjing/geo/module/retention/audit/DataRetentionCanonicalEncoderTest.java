package com.huanjing.geo.module.retention.audit;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;
import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WEBSITE_DOMAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataRetentionCanonicalEncoderTest {

    private final DataRetentionCanonicalEncoder encoder = new DataRetentionCanonicalEncoder();

    @Test
    void reproducesTheFourFrozenSha256VectorsFromIndependentBytes() throws Exception {
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
                cleanupOperationMaxIds=20
                cleanupPriority=before_primary
                hotRetentionDays=14
                lateWritePolicy=http_409
                preserveLatest=true
                preservedResultLedger=true
                requireAllChildrenPurged=true
                requireMarkerPresent=true
                requireSummaryConsistent=true
                sliceTransactionMode=single_slice
                stuckBatchSealDays=7
                """;
        assertEquals("5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd",
                sha256(websiteFingerprint));
        assertEquals("f34506d8fb52a24973a93dd07c1b97746f9e88502ba3dc54953b380a153d6b71",
                sha256(pollFingerprint));
        assertEquals(sha256(websiteFingerprint), encoder.websiteFingerprint(null).hexDigest());
        assertEquals(sha256(pollFingerprint), encoder.pollFingerprint(null, null).hexDigest());

        RetentionCompatibilityContract websiteContract = new RetentionCompatibilityContract(
                WEBSITE_DOMAIN, 1, encoder.websiteFingerprint(null).sha256(), "website-published-v1");
        RetentionCompatibilityContract pollContract = new RetentionCompatibilityContract(
                POLL_DOMAIN, 1, encoder.pollFingerprint(null, null).sha256(), "poll-results-v2");
        assertEquals("14a67e69ebea79dea3c01abef4cd982c8fcb22303ec519941e7f319c633abbe7",
                encoder.websiteScope(new WebsiteOperationCommand(List.of(101L, 205L, 309L, 410L, 512L),
                        Instant.parse("2026-08-10T03:30:00Z"), 512), websiteContract).hexDigest());
        assertEquals("d6074e9e793b773fc9016bef0831c70c1121fd2e8e6807a33c5d47a13ac76b5b",
                encoder.pollScope(new PollOperationCommand(1, LocalDate.parse("2026-05-26"), "A",
                        LocalDate.parse("2026-07-28")), pollContract).hexDigest());
    }

    @Test
    void normalizesPollTierWithRootLocaleAndKeepsScopeSensitive() {
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(POLL_DOMAIN, 1,
                encoder.pollFingerprint(null, null).sha256(), "poll-results-v2");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            String normalized = encoder.pollScope(new PollOperationCommand(1, LocalDate.parse("2026-05-26"),
                    " i ", LocalDate.parse("2026-07-28")), contract).utf8Text();
            assertEquals(true, normalized.contains("questionTier=I\n"));
        } finally {
            Locale.setDefault(previous);
        }
        assertNotEquals(
                encoder.pollScope(new PollOperationCommand(1, LocalDate.parse("2026-05-26"), "A",
                        LocalDate.parse("2026-07-28")), contract).hexDigest(),
                encoder.pollScope(new PollOperationCommand(1, LocalDate.parse("2026-05-26"), "A",
                        LocalDate.parse("2026-07-29")), contract).hexDigest());
    }

    @Test
    void rebuildsFingerprintOnlyFromTheExactFrozenEffectiveConfig() {
        var website = encoder.websiteFingerprint(null);
        assertEquals(website.hexDigest(), encoder.fingerprintFromEffectiveConfig(WEBSITE_DOMAIN,
                encoder.websiteEffectiveConfig(null)).hexDigest());
        var poll = encoder.pollFingerprint(null, null);
        assertEquals(poll.hexDigest(), encoder.fingerprintFromEffectiveConfig(POLL_DOMAIN,
                encoder.pollEffectiveConfig(null, null)).hexDigest());

        var corrupted = new java.util.LinkedHashMap<>(encoder.websiteEffectiveConfig(null));
        corrupted.put("requireMarkerPresent", false);
        assertThrows(RuntimeException.class,
                () -> encoder.fingerprintFromEffectiveConfig(WEBSITE_DOMAIN, corrupted));
        corrupted.put("requireMarkerPresent", true);
        corrupted.put("unexpected", true);
        assertThrows(RuntimeException.class,
                () -> encoder.fingerprintFromEffectiveConfig(WEBSITE_DOMAIN, corrupted));
    }

    private String sha256(String input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
