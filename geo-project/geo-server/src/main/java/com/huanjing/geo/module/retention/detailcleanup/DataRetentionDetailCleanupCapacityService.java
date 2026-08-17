package com.huanjing.geo.module.retention.detailcleanup;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class DataRetentionDetailCleanupCapacityService {
    private final DataRetentionDetailCleanupRunRepository runs;

    public DataRetentionDetailCleanupCapacityService(DataRetentionDetailCleanupRunRepository runs) {
        this.runs = runs;
    }

    public Long projectedFiveYearBytes(Instant now, long currentBytes) {
        DataRetentionDetailCleanupRunRepository.CapacityWindow window = runs.capacityWindow(now.minus(Duration.ofDays(30)));
        if (window == null || Duration.between(window.oldestAt(), window.latestAt()).compareTo(Duration.ofDays(7)) < 0) {
            return null;
        }
        return project(window.oldestBytes(), window.latestBytes(),
                Duration.between(window.oldestAt(), window.latestAt()), currentBytes);
    }

    public Long projectedIncludingCurrentSample(Instant now,long currentBytes) {
        DataRetentionDetailCleanupRunRepository.CapacityPoint oldest=runs.oldestCapacityPoint(now.minus(Duration.ofDays(30)));
        if(oldest==null||Duration.between(oldest.at(),now).compareTo(Duration.ofDays(7))<0) return null;
        return project(oldest.bytes(),currentBytes,Duration.between(oldest.at(),now),currentBytes);
    }

    static long project(long oldestBytes, long latestBytes, Duration elapsed, long currentBytes) {
        if (elapsed.isZero() || elapsed.isNegative()) throw new IllegalArgumentException("positive elapsed duration required");
        BigDecimal growth = BigDecimal.valueOf(Math.max(0, latestBytes - oldestBytes))
                .multiply(BigDecimal.valueOf(Duration.ofDays(1).toMillis()))
                .divide(BigDecimal.valueOf(elapsed.toMillis()), 12, RoundingMode.HALF_UP);
        BigDecimal raw = BigDecimal.valueOf(currentBytes)
                .add(growth.multiply(BigDecimal.valueOf(1825)));
        return raw.multiply(new BigDecimal("1.30")).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
