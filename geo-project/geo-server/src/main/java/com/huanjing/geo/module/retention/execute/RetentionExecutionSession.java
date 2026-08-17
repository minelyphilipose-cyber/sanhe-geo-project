package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.CommittedMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionProgress;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.service.TrustedTerminalUsageReceipt;

public interface RetentionExecutionSession {
    RetentionRunContext run();
    RetentionExecuteCommand command();
    ExecutionProgress progress();
    DataRetentionP0AuditService audit();
    <T> CommittedMutation<T> inMutationTransaction(MutationEstimate estimate, RetentionMutationWork<T> work);
    void consumeTerminalUsage(TrustedTerminalUsageReceipt receipt);
    boolean isStopped();
}
