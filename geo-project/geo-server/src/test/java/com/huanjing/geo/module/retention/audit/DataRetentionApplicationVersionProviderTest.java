package com.huanjing.geo.module.retention.audit;

import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataRetentionApplicationVersionProviderTest {

    @Test
    void requiresPrintableAsciiDeploymentIdentity() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", " commit-v2 ");
        assertEquals("commit-v2", new DataRetentionApplicationVersionProvider(environment).currentVersion());

        DataRetentionAuditException missing = assertThrows(DataRetentionAuditException.class,
                () -> new DataRetentionApplicationVersionProvider(new MockEnvironment()).currentVersion());
        assertEquals(DataRetentionAuditErrors.APPLICATION_VERSION_MISSING, missing.getErrorCode());

        MockEnvironment invalidEnvironment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", "版本-v2");
        DataRetentionAuditException invalid = assertThrows(DataRetentionAuditException.class,
                () -> new DataRetentionApplicationVersionProvider(invalidEnvironment).currentVersion());
        assertEquals(DataRetentionAuditErrors.APPLICATION_VERSION_INVALID, invalid.getErrorCode());
    }

    @Test
    void acceptsTheFrozenEnvironmentVariableName() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GEO_RETENTION_APPLICATION_VERSION", "release-env-1");
        assertEquals("release-env-1", new DataRetentionApplicationVersionProvider(environment).currentVersion());
    }
}
