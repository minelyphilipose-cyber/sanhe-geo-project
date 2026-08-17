package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DataRetentionDomainExecutorRegistry {
    private final Map<RetentionDomain, DataRetentionDomainExecutor> executors;

    public DataRetentionDomainExecutorRegistry(List<DataRetentionDomainExecutor> candidates) {
        Map<RetentionDomain, DataRetentionDomainExecutor> values = new EnumMap<>(RetentionDomain.class);
        for (DataRetentionDomainExecutor candidate : candidates) {
            if (candidate == null || candidate.domain() == null || values.putIfAbsent(candidate.domain(), candidate) != null) {
                throw new IllegalStateException("Duplicate or invalid data retention domain executor");
            }
        }
        this.executors = Map.copyOf(values);
    }

    public DataRetentionDomainExecutor require(RetentionDomain domain) {
        DataRetentionDomainExecutor executor = executors.get(domain);
        if (executor == null) {
            throw new DataRetentionExecutionException(503, "DOMAIN_EXECUTOR_UNAVAILABLE",
                    "No typed data retention executor is registered for " + domain);
        }
        return executor;
    }
}
