package com.huanjing.geo.module.retention.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.storage.StorageProperties;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.controller.DataRetentionOperationsController.OperationalStatus;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessCode;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.Severity;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupReadinessService;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchAuthorizationPolicy;
import com.huanjing.geo.module.retention.service.DataRetentionOperationsStatusService;
import com.huanjing.geo.module.retention.service.DataRetentionOperationsStatusService.SchemaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataRetentionOperationsControllerTest {

    @Test
    void reportsClosedMutationGatesAndCurrentReadiness() {
        DataRetentionProperties properties = new DataRetentionProperties();
        DataRetentionDetailCleanupReadinessService readinessService =
                mock(DataRetentionDetailCleanupReadinessService.class);
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionOperationsStatusService operationsStatusService =
                mock(DataRetentionOperationsStatusService.class);
        SchemaStatus schema = new SchemaStatus(true, "368", "368", List.of(), true, null);
        ReadinessEvaluation readiness = new ReadinessEvaluation(
                ReadinessCode.CLEANUP_NEVER_SUCCEEDED,
                Severity.BLOCK_ONLY,
                List.of(ReadinessCode.CLEANUP_NEVER_SUCCEEDED),
                12,
                34,
                null,
                1024L,
                null);
        when(readinessService.evaluate()).thenReturn(readiness);
        when(operationsStatusService.schemaStatus()).thenReturn(schema);
        DataRetentionApplicationVersionProvider versionProvider = mock(DataRetentionApplicationVersionProvider.class);
        when(versionProvider.currentVersion()).thenReturn("local-acceptance-20260815.1");

        R<OperationalStatus> response = new DataRetentionOperationsController(
                properties, new StorageProperties(), operationsStatusService, readinessService, authorization,
                versionProvider).status();

        verify(authorization).requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.READ);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData().allStaticMutationGatesClosed()).isTrue();
        assertThat(response.getData().schema()).isSameAs(schema);
        assertThat(response.getData().auditWriter().configured()).isTrue();
        assertThat(response.getData().auditWriter().applicationVersion()).isEqualTo("local-acceptance-20260815.1");
        assertThat(response.getData().staticSafetyGates().schedulerEnabled()).isFalse();
        assertThat(response.getData().storage().provider()).isEqualTo(StorageProperties.Provider.MINIO);
        assertThat(response.getData().storage().cosTargetConfigured()).isFalse();
        assertThat(response.getData().storage().objectStorageMigrationApplicable()).isFalse();
        assertThat(response.getData().detailCleanupReadiness()).isSameAs(readiness);
    }

    @Test
    void schedulerDryRunDoesNotOpenMutationGateButAnyWriteGateDoes() {
        DataRetentionProperties properties = new DataRetentionProperties();
        StorageProperties storageProperties = new StorageProperties();
        properties.getScheduler().setEnabled(true);
        DataRetentionDetailCleanupReadinessService readinessService =
                mock(DataRetentionDetailCleanupReadinessService.class);
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionOperationsStatusService operationsStatusService =
                mock(DataRetentionOperationsStatusService.class);
        DataRetentionApplicationVersionProvider versionProvider = mock(DataRetentionApplicationVersionProvider.class);
        when(versionProvider.currentVersion()).thenReturn("test-v1");

        OperationalStatus dryRunOnly = new DataRetentionOperationsController(
                properties, storageProperties, operationsStatusService, readinessService, authorization,
                versionProvider).status().getData();
        assertThat(dryRunOnly.allStaticMutationGatesClosed()).isTrue();

        storageProperties.getMigration().setExecuteEnabled(true);
        OperationalStatus storageExecuteEnabled = new DataRetentionOperationsController(
                properties, storageProperties, operationsStatusService, readinessService, authorization,
                versionProvider).status().getData();
        assertThat(storageExecuteEnabled.allStaticMutationGatesClosed()).isFalse();
        assertThat(storageExecuteEnabled.staticSafetyGates().objectStorageMigrationExecuteEnabled()).isTrue();

        storageProperties.getMigration().setExecuteEnabled(false);
        properties.getPollResults().setExecuteEnabled(true);
        OperationalStatus executeEnabled = new DataRetentionOperationsController(
                properties, storageProperties, operationsStatusService, readinessService, authorization,
                versionProvider).status().getData();
        assertThat(executeEnabled.allStaticMutationGatesClosed()).isFalse();
        assertThat(executeEnabled.staticSafetyGates().pollExecuteEnabled()).isTrue();
    }

    @Test
    void authorizationFailureDoesNotQueryOperationalReadiness() {
        DataRetentionDetailCleanupReadinessService readinessService =
                mock(DataRetentionDetailCleanupReadinessService.class);
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionOperationsStatusService operationsStatusService =
                mock(DataRetentionOperationsStatusService.class);
        DataRetentionApplicationVersionProvider versionProvider = mock(DataRetentionApplicationVersionProvider.class);
        SecurityException denied = new SecurityException("denied");
        doThrow(denied).when(authorization).requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.READ);

        DataRetentionOperationsController controller = new DataRetentionOperationsController(
                new DataRetentionProperties(), new StorageProperties(), operationsStatusService,
                readinessService, authorization, versionProvider);

        assertThatThrownBy(controller::status).isSameAs(denied);
        verifyNoInteractions(readinessService);
        verifyNoInteractions(operationsStatusService);
        verifyNoInteractions(versionProvider);
    }

    @Test
    void reportsMissingAuditWriterVersionWithoutBreakingReadOnlyStatus() {
        DataRetentionDetailCleanupReadinessService readinessService =
                mock(DataRetentionDetailCleanupReadinessService.class);
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionOperationsStatusService operationsStatusService =
                mock(DataRetentionOperationsStatusService.class);
        DataRetentionApplicationVersionProvider versionProvider = mock(DataRetentionApplicationVersionProvider.class);
        when(versionProvider.currentVersion()).thenThrow(new DataRetentionAuditException(
                DataRetentionAuditErrors.APPLICATION_VERSION_MISSING, "missing"));

        OperationalStatus status = new DataRetentionOperationsController(
                new DataRetentionProperties(), new StorageProperties(), operationsStatusService,
                readinessService, authorization, versionProvider).status().getData();

        assertThat(status.auditWriter().configured()).isFalse();
        assertThat(status.auditWriter().applicationVersion()).isNull();
        assertThat(status.auditWriter().errorCode()).isEqualTo(DataRetentionAuditErrors.APPLICATION_VERSION_MISSING);
    }
}
