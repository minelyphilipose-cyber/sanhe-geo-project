package com.huanjing.geo.module.retention.audit.support;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DataRetentionApplicationVersionProvider {

    private static final String PROPERTY = "geo.retention.audit.application-version";
    private final Environment environment;

    public DataRetentionApplicationVersionProvider(Environment environment) {
        this.environment = environment;
    }

    public String currentVersion() {
        String configured = environment.getProperty(PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            configured = environment.getProperty("GEO_RETENTION_APPLICATION_VERSION");
        }
        if (configured == null || configured.trim().isEmpty()) {
            throw new DataRetentionAuditException(DataRetentionAuditErrors.APPLICATION_VERSION_MISSING,
                    "retention audit application version is missing");
        }
        String value = configured.trim();
        byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length > 64 || ascii.length != value.length()
                || value.chars().anyMatch(character -> character < 0x20 || character > 0x7e)) {
            throw new DataRetentionAuditException(DataRetentionAuditErrors.APPLICATION_VERSION_INVALID,
                    "retention audit application version must be 1..64 printable ASCII bytes");
        }
        return value;
    }
}
