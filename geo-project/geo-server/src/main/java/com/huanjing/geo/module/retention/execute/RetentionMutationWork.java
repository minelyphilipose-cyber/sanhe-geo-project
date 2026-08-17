package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;

@FunctionalInterface
public interface RetentionMutationWork<T> {
    PendingMutation<T> execute();
}
