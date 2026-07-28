package com.huanjing.geo.module.report.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportPeriodFreezeJobTest {

    @Test
    void retiredQuarterlyFreezeJobIsDisabledUnlessExplicitlyEnabled() {
        ConditionalOnProperty condition =
                ReportPeriodFreezeJob.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("geo.retention.report-freeze", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }
}
