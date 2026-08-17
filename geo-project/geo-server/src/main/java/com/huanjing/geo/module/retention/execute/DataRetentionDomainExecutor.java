package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AuditOnlyReconciliationCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PersistedRunState;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionReconcileCommand;

public interface DataRetentionDomainExecutor {
    RetentionDomain domain();
    RetentionExecutionResult executeFresh(RetentionExecutionSession session);
    RetentionExecutionResult resumeRunning(RetentionExecutionSession session, PersistedRunState state);

    default AuditOnlyReconciliationCommand investigateReconciliation(RetentionReconcileCommand command) {
        throw new DataRetentionExecutionException(503, "DOMAIN_EXECUTOR_UNAVAILABLE",
                "No typed reconciliation investigator is registered for " + domain());
    }
}
