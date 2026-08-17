package com.huanjing.geo.module.retention.detailcleanup;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DataRetentionDetailCleanupCapacityServiceTest {
    @Test void projectsThirtyDayGrowthOverFiveYearsWithThirtyPercentReserve() {
        long projected = DataRetentionDetailCleanupCapacityService.project(
                1_000, 1_700, Duration.ofDays(7), 1_700);
        assertThat(projected).isEqualTo(239_460);
    }

    @Test void negativeGrowthIsClampedToZeroBeforeReserve() {
        assertThat(DataRetentionDetailCleanupCapacityService.project(
                2_000, 1_000, Duration.ofDays(10), 1_000)).isEqualTo(1_300);
    }
}
