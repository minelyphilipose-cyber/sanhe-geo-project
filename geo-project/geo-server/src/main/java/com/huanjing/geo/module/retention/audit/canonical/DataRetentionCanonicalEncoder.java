package com.huanjing.geo.module.retention.audit.canonical;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;
import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WEBSITE_DOMAIN;

@Component
public class DataRetentionCanonicalEncoder {

    public static final int SCHEMA_VERSION = 1;
    public static final String WEBSITE_POLICY_VERSION = "website-published-v1";
    public static final String POLL_POLICY_VERSION = "poll-results-v2";
    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public CanonicalValue websiteFingerprint(Integer requestedRetentionHours) {
        int retentionHours = requestedRetentionHours == null ? 24 : Math.max(24, requestedRetentionHours);
        if (retentionHours <= 0) {
            throw invalid("website retention hours must be positive");
        }
        String text = """
                batchMaxItems=20
                batchMinItems=5
                businessTimezone=Asia/Shanghai
                postCheckMode=locked_rows_only
                requireBodyPurged=true
                requireLightweightEvidencePreserved=true
                requireMarkerPresent=true
                requirePayloadPurged=true
                requireRawResponsePurged=true
                retentionHours=%d
                splitStrategy=halve_to_min_5
                """.formatted(retentionHours);
        return value(text);
    }

    public CanonicalValue pollFingerprint(Integer requestedHotRetentionDays, Integer requestedStuckBatchSealDays) {
        int hotRetentionDays = requestedHotRetentionDays == null ? 14 : Math.max(14, requestedHotRetentionDays);
        int stuckBatchSealDays = requestedStuckBatchSealDays == null || requestedStuckBatchSealDays <= 0
                ? 7 : requestedStuckBatchSealDays;
        String text = """
                businessTimezone=Asia/Shanghai
                cleanupOperationMaxIds=20
                cleanupPriority=before_primary
                hotRetentionDays=%d
                lateWritePolicy=http_409
                preserveLatest=true
                preservedResultLedger=true
                requireAllChildrenPurged=true
                requireMarkerPresent=true
                requireSummaryConsistent=true
                sliceTransactionMode=single_slice
                stuckBatchSealDays=%d
                """.formatted(hotRetentionDays, stuckBatchSealDays);
        return value(text);
    }

    public Map<String, Object> websiteEffectiveConfig(Integer requestedRetentionHours) {
        int retentionHours = requestedRetentionHours == null ? 24 : Math.max(24, requestedRetentionHours);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("batchMaxItems", 20);
        config.put("batchMinItems", 5);
        config.put("businessTimezone", "Asia/Shanghai");
        config.put("postCheckMode", "locked_rows_only");
        config.put("requireBodyPurged", true);
        config.put("requireLightweightEvidencePreserved", true);
        config.put("requireMarkerPresent", true);
        config.put("requirePayloadPurged", true);
        config.put("requireRawResponsePurged", true);
        config.put("retentionHours", retentionHours);
        config.put("splitStrategy", "halve_to_min_5");
        return Map.copyOf(config);
    }

    public Map<String, Object> pollEffectiveConfig(Integer requestedHotRetentionDays,
                                                    Integer requestedStuckBatchSealDays) {
        int hotRetentionDays = requestedHotRetentionDays == null ? 14 : Math.max(14, requestedHotRetentionDays);
        int stuckBatchSealDays = requestedStuckBatchSealDays == null || requestedStuckBatchSealDays <= 0
                ? 7 : requestedStuckBatchSealDays;
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("businessTimezone", "Asia/Shanghai");
        config.put("cleanupOperationMaxIds", 20);
        config.put("cleanupPriority", "before_primary");
        config.put("hotRetentionDays", hotRetentionDays);
        config.put("lateWritePolicy", "http_409");
        config.put("preserveLatest", true);
        config.put("preservedResultLedger", true);
        config.put("requireAllChildrenPurged", true);
        config.put("requireMarkerPresent", true);
        config.put("requireSummaryConsistent", true);
        config.put("sliceTransactionMode", "single_slice");
        config.put("stuckBatchSealDays", stuckBatchSealDays);
        return Map.copyOf(config);
    }

    public CanonicalValue fingerprintFromEffectiveConfig(String domain, Map<String, Object> effectiveConfig) {
        if (WEBSITE_DOMAIN.equals(domain)) {
            requireExactConfig(effectiveConfig, websiteEffectiveConfig(integer(effectiveConfig, "retentionHours")));
            return websiteFingerprint(integer(effectiveConfig, "retentionHours"));
        }
        if (POLL_DOMAIN.equals(domain)) {
            requireExactConfig(effectiveConfig, pollEffectiveConfig(integer(effectiveConfig, "hotRetentionDays"),
                    integer(effectiveConfig, "stuckBatchSealDays")));
            return pollFingerprint(integer(effectiveConfig, "hotRetentionDays"),
                    integer(effectiveConfig, "stuckBatchSealDays"));
        }
        throw invalid("unsupported retention effective-config domain");
    }

    public CanonicalValue websiteScope(WebsiteOperationCommand command,
                                       RetentionCompatibilityContract contract) {
        requireContract(contract, WEBSITE_DOMAIN, WEBSITE_POLICY_VERSION);
        if (command == null || command.cutoff() == null || command.articleIds() == null) {
            throw invalid("website operation scope is incomplete");
        }
        List<Long> ids = command.articleIds().stream().sorted().toList();
        if (ids.size() < 1 || ids.size() > 20 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw invalid("website article ids must be unique positive values with size 1..20");
        }
        if (command.upperBoundBusinessKey() < ids.get(ids.size() - 1)) {
            throw invalid("website upper bound is below the largest article id");
        }
        StringBuilder text = new StringBuilder()
                .append("hashSchemaVersion=1\n")
                .append("domain=website_published_content\n")
                .append("businessKeyType=article_id\n")
                .append("itemCount=").append(ids.size()).append('\n');
        for (int i = 0; i < ids.size(); i++) {
            text.append("businessKey[").append(i).append("]=").append(ids.get(i)).append('\n');
        }
        text.append("cutoff=").append(format(command.cutoff())).append('\n')
                .append("upperBound=").append(command.upperBoundBusinessKey()).append('\n')
                .append("policyVersion=").append(contract.policyVersion()).append('\n')
                .append("configFingerprintSchemaVersion=").append(contract.configFingerprintSchemaVersion()).append('\n')
                .append("configFingerprint=").append(hex(contract.configFingerprint())).append('\n');
        return value(text.toString());
    }

    public CanonicalValue pollScope(PollOperationCommand command,
                                    RetentionCompatibilityContract contract) {
        requireContract(contract, POLL_DOMAIN, POLL_POLICY_VERSION);
        if (command == null || command.projectId() <= 0 || command.batchDate() == null
                || command.retentionCutoffDate() == null || command.questionTier() == null) {
            throw invalid("poll operation scope is incomplete");
        }
        String tier = command.questionTier().trim().toUpperCase(Locale.ROOT);
        if (tier.isEmpty() || tier.length() > 16) {
            throw invalid("poll question tier is invalid");
        }
        String text = """
                hashSchemaVersion=1
                domain=poll_results
                projectId=%d
                batchDate=%s
                questionTier=%s
                cutoff=%s
                upperBound=<null>
                policyVersion=%s
                configFingerprintSchemaVersion=%d
                configFingerprint=%s
                """.formatted(command.projectId(), command.batchDate(), tier, command.retentionCutoffDate(),
                contract.policyVersion(), contract.configFingerprintSchemaVersion(), hex(contract.configFingerprint()));
        return value(text);
    }

    public CanonicalValue pollCleanupScope(PollCleanupOperationCommand command,
                                           RetentionCompatibilityContract contract) {
        requireContract(contract, POLL_DOMAIN, POLL_POLICY_VERSION);
        if (command == null || command.originOperationId() == null || command.projectId() <= 0
                || command.batchDate() == null || command.retentionCutoffDate() == null
                || command.questionTier() == null || command.pollResultIds() == null) {
            throw invalid("poll cleanup operation scope is incomplete");
        }
        String originOperationId = command.originOperationId().trim().toLowerCase(Locale.ROOT);
        try {
            if (!java.util.UUID.fromString(originOperationId).toString().equals(originOperationId)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalidId) {
            throw invalid("poll cleanup origin operation id is invalid");
        }
        String tier = command.questionTier().trim().toUpperCase(Locale.ROOT);
        List<Long> ids = command.pollResultIds().stream().sorted().toList();
        if (tier.isEmpty() || tier.length() > 16 || ids.size() < 1 || ids.size() > 20
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw invalid("poll cleanup result ids or question tier are invalid");
        }
        StringBuilder text = new StringBuilder()
                .append("hashSchemaVersion=1\n")
                .append("domain=poll_results\n")
                .append("operationType=poll_preserved_cleanup\n")
                .append("originOperationId=").append(originOperationId).append('\n')
                .append("projectId=").append(command.projectId()).append('\n')
                .append("batchDate=").append(command.batchDate()).append('\n')
                .append("questionTier=").append(tier).append('\n')
                .append("cutoff=").append(command.retentionCutoffDate()).append('\n')
                .append("itemCount=").append(ids.size()).append('\n');
        for (int index = 0; index < ids.size(); index++) {
            text.append("pollResultId[").append(index).append("]=").append(ids.get(index)).append('\n');
        }
        text.append("policyVersion=").append(contract.policyVersion()).append('\n')
                .append("configFingerprintSchemaVersion=")
                .append(contract.configFingerprintSchemaVersion()).append('\n')
                .append("configFingerprint=").append(hex(contract.configFingerprint())).append('\n');
        return value(text.toString());
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static String hex(byte[] bytes) {
        if (bytes == null || bytes.length != 32) {
            throw invalid("SHA-256 value must contain exactly 32 bytes");
        }
        return HexFormat.of().formatHex(bytes);
    }

    private void requireContract(RetentionCompatibilityContract contract, String domain, String policy) {
        if (contract == null || !domain.equals(contract.domain()) || contract.configFingerprintSchemaVersion() != 1
                || contract.configFingerprint() == null || contract.configFingerprint().length != 32
                || !policy.equals(contract.policyVersion())) {
            throw invalid("retention compatibility contract does not match the domain");
        }
    }

    private String format(Instant instant) {
        return INSTANT_FORMAT.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    private CanonicalValue value(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new CanonicalValue(bytes, sha256(bytes));
    }

    private static int integer(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        if (!(value instanceof Number number) || number.longValue() < 1 || number.longValue() > Integer.MAX_VALUE
                || number.doubleValue() != number.longValue()) {
            throw invalid("retention effective config integer is invalid: " + key);
        }
        return number.intValue();
    }

    private static void requireExactConfig(Map<String, Object> actual, Map<String, Object> expected) {
        if (actual == null || !actual.keySet().equals(expected.keySet())) {
            throw invalid("retention effective config keys do not match schema v1");
        }
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object value = actual.get(entry.getKey());
            if (entry.getValue() instanceof Number expectedNumber) {
                if (!(value instanceof Number actualNumber)
                        || expectedNumber.longValue() != actualNumber.longValue()
                        || actualNumber.doubleValue() != actualNumber.longValue()) {
                    throw invalid("retention effective config value does not match schema v1: " + entry.getKey());
                }
            } else if (!entry.getValue().equals(value)) {
                throw invalid("retention effective config value does not match schema v1: " + entry.getKey());
            }
        }
    }

    private static DataRetentionAuditException invalid(String message) {
        return new DataRetentionAuditException(DataRetentionAuditErrors.CONTEXT_INVALID, message);
    }

    public record CanonicalValue(byte[] bytes, byte[] sha256) {
        public CanonicalValue {
            bytes = bytes.clone();
            sha256 = sha256.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] sha256() {
            return sha256.clone();
        }

        public String utf8Text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public String hexDigest() {
            return HexFormat.of().formatHex(sha256);
        }
    }
}
