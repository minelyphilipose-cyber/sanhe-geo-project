package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DataRetentionStaticExecuteGate {
    private final DataRetentionProperties properties;

    public DataRetentionStaticExecuteGate(DataRetentionProperties properties) {
        this.properties = properties;
    }

    public void requireEnabled(RetentionDomain domain, ExecutionSource source) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(source, "source");
        if (source == ExecutionSource.SCHEDULER && !properties.getScheduler().isEnabled()) {
            throw new DataRetentionExecutionException(409, "EXECUTE_STATIC_GLOBAL_DISABLED",
                    "Data retention scheduler cron gate is disabled");
        }
        if (!properties.getScheduler().isExecuteEnabled()) {
            throw new DataRetentionExecutionException(409, "EXECUTE_STATIC_GLOBAL_DISABLED",
                    "Global data retention execute gate is disabled");
        }
        boolean enabled = switch (domain) {
            case WEBSITE_PUBLISHED_CONTENT -> properties.getWebsitePublishedCleanup().isExecuteEnabled();
            case POLL_RESULTS -> properties.getPollResults().isExecuteEnabled();
        };
        if (!enabled) {
            throw new DataRetentionExecutionException(409, "EXECUTE_STATIC_DOMAIN_DISABLED",
                    "Data retention execute gate is disabled for " + domain.databaseValue());
        }
    }
}
