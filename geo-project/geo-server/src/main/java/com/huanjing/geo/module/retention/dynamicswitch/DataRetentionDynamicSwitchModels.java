package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.common.exception.BizException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DataRetentionDynamicSwitchModels {

    public static final long ENABLE_APPROVAL_EXPIRY_MILLIS = 1_800_000L;
    public static final long ENABLE_CAS_GRACE_MILLIS = 60_000L;
    public static final String REDIS_KEY_PREFIX = "geo:data-retention:{execute-switch}:v1:";
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern CANONICAL_VERSION = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern UTC_MILLIS = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");
    private static final DateTimeFormatter UTC_MILLIS_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private DataRetentionDynamicSwitchModels() {
    }

    public enum RetentionDomain {
        WEBSITE_PUBLISHED_CONTENT("website_published_content"),
        POLL_RESULTS("poll_results");

        private final String databaseValue;

        RetentionDomain(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }
    }

    public enum SwitchKey {
        GLOBAL_EXECUTE("global_execute", null),
        WEBSITE_PUBLISHED_CONTENT_EXECUTE("website_published_content_execute", RetentionDomain.WEBSITE_PUBLISHED_CONTENT),
        POLL_RESULTS_EXECUTE("poll_results_execute", RetentionDomain.POLL_RESULTS);

        private final String databaseValue;
        private final RetentionDomain domain;

        SwitchKey(String databaseValue, RetentionDomain domain) {
            this.databaseValue = databaseValue;
            this.domain = domain;
        }

        public String databaseValue() {
            return databaseValue;
        }

        public String redisKey() {
            return REDIS_KEY_PREFIX + databaseValue;
        }

        public RetentionDomain domain() {
            return domain;
        }

        public static SwitchKey fromDatabaseValue(String value) {
            for (SwitchKey key : values()) {
                if (key.databaseValue.equals(value)) {
                    return key;
                }
            }
            throw new DynamicSwitchException(400, "UNKNOWN_SWITCH", "Unknown dynamic switch key");
        }

        public static SwitchKey forDomain(RetentionDomain domain) {
            Objects.requireNonNull(domain, "domain");
            return switch (domain) {
                case WEBSITE_PUBLISHED_CONTENT -> WEBSITE_PUBLISHED_CONTENT_EXECUTE;
                case POLL_RESULTS -> POLL_RESULTS_EXECUTE;
            };
        }
    }

    public record Payload(String changeId, long version, boolean enabled, Instant changedAt) {
        public Payload {
            changeId = canonicalChangeId(changeId);
            if (version < 1) {
                throw new IllegalArgumentException("version must be positive");
            }
            Objects.requireNonNull(changedAt, "changedAt");
            if (!changedAt.equals(changedAt.truncatedTo(ChronoUnit.MILLIS))) {
                throw new IllegalArgumentException("changedAt must have millisecond precision");
            }
            int year = ZonedDateTime.ofInstant(changedAt, ZoneOffset.UTC).getYear();
            if (year < 1000 || year > 9999) {
                throw new IllegalArgumentException("changedAt is outside MySQL DATETIME range");
            }
        }

        public String versionText() {
            return Long.toString(version);
        }

        public String enabledText() {
            return enabled ? "1" : "0";
        }

        public String changedAtText() {
            return UTC_MILLIS_FORMATTER.format(changedAt);
        }

        public static Payload decode(String changeId, String version, String enabled, String changedAt) {
            if (!CANONICAL_VERSION.matcher(version == null ? "" : version).matches()) {
                throw new IllegalArgumentException("non-canonical version");
            }
            final long parsedVersion;
            try {
                parsedVersion = Long.parseLong(version);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("version exceeds Long.MAX_VALUE", e);
            }
            if (!("0".equals(enabled) || "1".equals(enabled))) {
                throw new IllegalArgumentException("non-canonical enabled value");
            }
            if (!UTC_MILLIS.matcher(changedAt == null ? "" : changedAt).matches()) {
                throw new IllegalArgumentException("non-canonical changed_at");
            }
            try {
                Instant instant = Instant.parse(changedAt);
                if (!UTC_MILLIS_FORMATTER.format(instant).equals(changedAt)) {
                    throw new IllegalArgumentException("non-canonical changed_at");
                }
                return new Payload(changeId, parsedVersion, "1".equals(enabled), instant);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("invalid changed_at", e);
            }
        }
    }

    public enum SnapshotIntegrity {
        VALID,
        MISSING,
        MALFORMED,
        UNAVAILABLE
    }

    public record Snapshot(SwitchKey switchKey, SnapshotIntegrity integrity, Payload payload, String errorClass) {
        public Snapshot {
            Objects.requireNonNull(switchKey, "switchKey");
            Objects.requireNonNull(integrity, "integrity");
            if (integrity == SnapshotIntegrity.VALID && payload == null) {
                throw new IllegalArgumentException("valid snapshot requires payload");
            }
            if (integrity != SnapshotIntegrity.VALID && payload != null) {
                throw new IllegalArgumentException("non-valid snapshot cannot carry payload");
            }
        }

        public boolean enabled() {
            return integrity == SnapshotIntegrity.VALID && payload.enabled();
        }

        public long effectiveVersion() {
            return integrity == SnapshotIntegrity.VALID ? payload.version() : 0L;
        }

        public static Snapshot missing(SwitchKey key) {
            return new Snapshot(key, SnapshotIntegrity.MISSING, null, null);
        }

        public static Snapshot malformed(SwitchKey key) {
            return new Snapshot(key, SnapshotIntegrity.MALFORMED, null, null);
        }

        public static Snapshot unavailable(SwitchKey key, Throwable error) {
            return new Snapshot(key, SnapshotIntegrity.UNAVAILABLE, null,
                    error == null ? "unknown" : error.getClass().getSimpleName());
        }
    }

    public record SnapshotPair(Snapshot global, Snapshot domain) {
    }

    public enum DecisionCode {
        ENABLED,
        GLOBAL_DISABLED,
        DOMAIN_DISABLED,
        REDIS_UNAVAILABLE,
        MALFORMED_GLOBAL_STATE,
        MALFORMED_DOMAIN_STATE,
        UNSUPPORTED_DOMAIN
    }

    public record Decision(RetentionDomain domain, boolean enabled, DecisionCode code, Instant checkedAt,
                           Snapshot global, Snapshot domainSwitch) {
    }

    public enum CasResultCode {
        APPLIED,
        ALREADY_APPLIED,
        STALE_TARGET,
        EXPECTED_MISMATCH,
        ENABLE_APPROVAL_EXPIRED,
        REDIS_TIME_ROLLBACK,
        SAME_VERSION_DIFFERENT_PAYLOAD,
        MALFORMED_CURRENT
    }

    public record CasResult(CasResultCode code, Snapshot observed) {
        public boolean exactSuccess() {
            return code == CasResultCode.APPLIED || code == CasResultCode.ALREADY_APPLIED;
        }
    }

    public enum ChangeStatus {
        REQUESTED("requested"), APPLIED("applied"), FAILED("failed");
        private final String databaseValue;
        ChangeStatus(String databaseValue) { this.databaseValue = databaseValue; }
        public String databaseValue() { return databaseValue; }
        public static ChangeStatus fromDatabaseValue(String value) {
            for (ChangeStatus status : values()) if (status.databaseValue.equals(value)) return status;
            throw new DynamicSwitchException(503, "AUDIT_INCONSISTENT", "Unknown dynamic switch status");
        }
    }

    public enum RequestSource {
        MANUAL("manual"), EMERGENCY("emergency"), RECONCILE("reconcile");
        private final String databaseValue;
        RequestSource(String databaseValue) { this.databaseValue = databaseValue; }
        public String databaseValue() { return databaseValue; }
        public static RequestSource fromDatabaseValue(String value) {
            for (RequestSource source : values()) if (source.databaseValue.equals(value)) return source;
            throw new DynamicSwitchException(503, "AUDIT_INCONSISTENT", "Unknown dynamic switch request source");
        }
    }

    public enum RequestedSubstate {
        AWAITING_APPROVAL, APPROVED_ENABLE, EXPIRED_ENABLE, PENDING_DISABLE, UNCERTAIN, TERMINAL
    }

    public static String canonicalChangeId(String value) {
        String candidate = value == null ? "" : value.trim();
        if (!CANONICAL_UUID.matcher(candidate).matches()) {
            throw new DynamicSwitchException(400, "INVALID_CHANGE_ID", "changeId must be a lowercase canonical UUID");
        }
        try {
            if (!UUID.fromString(candidate).toString().equals(candidate)) {
                throw new DynamicSwitchException(400, "INVALID_CHANGE_ID", "changeId must be canonical");
            }
        } catch (IllegalArgumentException e) {
            throw new DynamicSwitchException(400, "INVALID_CHANGE_ID", "changeId must be canonical");
        }
        return candidate;
    }

    public static final class DynamicSwitchException extends BizException {
        private final int httpStatus;
        private final String resultCode;

        public DynamicSwitchException(int httpStatus, String resultCode, String message) {
            super(httpStatus, message, httpStatus, resultCode);
            this.httpStatus = httpStatus;
            this.resultCode = resultCode == null ? "DYNAMIC_SWITCH_ERROR" : resultCode.toUpperCase(Locale.ROOT);
        }

        public DynamicSwitchException(int httpStatus, String resultCode, String message, Throwable cause) {
            super(httpStatus, message, httpStatus, resultCode, cause);
            this.httpStatus = httpStatus;
            this.resultCode = resultCode == null ? "DYNAMIC_SWITCH_ERROR" : resultCode.toUpperCase(Locale.ROOT);
        }

        public int httpStatus() { return httpStatus; }
        public String resultCode() { return resultCode; }
    }
}
