package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PersistedRunState;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DataRetentionDomainExecutorRegistryTest {
    @Test
    void missingExecutorFailsClosed() {
        assertThrows(DataRetentionExecutionException.class,
                () -> new DataRetentionDomainExecutorRegistry(List.of()).require(RetentionDomain.POLL_RESULTS));
    }

    @Test
    void duplicateExecutorFailsApplicationConstruction() {
        DataRetentionDomainExecutor first = fake();
        assertThrows(IllegalStateException.class,
                () -> new DataRetentionDomainExecutorRegistry(List.of(first, fake())));
    }

    private DataRetentionDomainExecutor fake() {
        return new DataRetentionDomainExecutor() {
            public RetentionDomain domain() { return RetentionDomain.POLL_RESULTS; }
            public RetentionExecutionResult executeFresh(RetentionExecutionSession session) { return null; }
            public RetentionExecutionResult resumeRunning(RetentionExecutionSession session, PersistedRunState state) {
                return null;
            }
        };
    }
}
