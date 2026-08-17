package com.huanjing.geo.module.retention.audit.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
public class DataRetentionAuditClock {

    private final Clock clock;

    public DataRetentionAuditClock() {
        this(Clock.systemUTC());
    }

    public DataRetentionAuditClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return truncate(Instant.now(clock));
    }

    public Instant truncate(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    public LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(truncate(value), ZoneOffset.UTC);
    }

    public Instant fromDatabaseTime(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
