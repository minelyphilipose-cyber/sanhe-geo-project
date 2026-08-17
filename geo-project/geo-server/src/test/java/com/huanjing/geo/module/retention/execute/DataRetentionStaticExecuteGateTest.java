package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataRetentionStaticExecuteGateTest {
    @Test
    void requiresGlobalAndDomainFlags() {
        DataRetentionProperties properties = new DataRetentionProperties();
        DataRetentionStaticExecuteGate gate = new DataRetentionStaticExecuteGate(properties);
        assertThrows(DataRetentionExecutionException.class,
                () -> gate.requireEnabled(RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL));

        properties.getScheduler().setExecuteEnabled(true);
        assertThrows(DataRetentionExecutionException.class,
                () -> gate.requireEnabled(RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL));

        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        assertDoesNotThrow(() -> gate.requireEnabled(
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL));
        assertThrows(DataRetentionExecutionException.class,
                () -> gate.requireEnabled(RetentionDomain.POLL_RESULTS, ExecutionSource.MANUAL));
    }

    @Test
    void schedulerAlsoRequiresCronGateButManualDoesNot() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getScheduler().setExecuteEnabled(true);
        properties.getWebsitePublishedCleanup().setExecuteEnabled(true);
        DataRetentionStaticExecuteGate gate = new DataRetentionStaticExecuteGate(properties);

        assertDoesNotThrow(() -> gate.requireEnabled(
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL));
        assertThrows(DataRetentionExecutionException.class, () -> gate.requireEnabled(
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.SCHEDULER));

        properties.getScheduler().setEnabled(true);
        assertDoesNotThrow(() -> gate.requireEnabled(
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.SCHEDULER));
    }
}
