package com.huanjing.geo.module.retention.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.storage.StorageProperties;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupReadinessService;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchAuthorizationPolicy;
import com.huanjing.geo.module.retention.service.DataRetentionOperationsStatusService;
import com.huanjing.geo.module.retention.service.DataRetentionOperationsStatusService.SchemaStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DataRetentionOperations")
@RestController
@RequestMapping("/api/data-retention/operations")
@RequiredArgsConstructor
public class DataRetentionOperationsController {

    private final DataRetentionProperties properties;
    private final StorageProperties storageProperties;
    private final DataRetentionOperationsStatusService operationsStatusService;
    private final DataRetentionDetailCleanupReadinessService readinessService;
    private final DataRetentionDynamicSwitchAuthorizationPolicy authorization;
    private final DataRetentionApplicationVersionProvider applicationVersionProvider;

    @GetMapping("/status")
    public R<OperationalStatus> status() {
        authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.READ);
        StaticSafetyGates gates = StaticSafetyGates.from(properties, storageProperties);
        StorageRuntime storage = StorageRuntime.from(storageProperties);
        return R.ok(new OperationalStatus(
                operationsStatusService.schemaStatus(),
                AuditWriterStatus.from(applicationVersionProvider),
                gates,
                storage,
                gates.allMutationGatesClosed(),
                readinessService.evaluate()));
    }

    public record OperationalStatus(
            SchemaStatus schema,
            AuditWriterStatus auditWriter,
            StaticSafetyGates staticSafetyGates,
            StorageRuntime storage,
            boolean allStaticMutationGatesClosed,
            ReadinessEvaluation detailCleanupReadiness) {
    }

    public record AuditWriterStatus(boolean configured, String applicationVersion, String errorCode) {
        static AuditWriterStatus from(DataRetentionApplicationVersionProvider provider) {
            try {
                return new AuditWriterStatus(true, provider.currentVersion(), null);
            } catch (DataRetentionAuditException error) {
                return new AuditWriterStatus(false, null, error.getErrorCode());
            }
        }
    }

    public record StorageRuntime(
            StorageProperties.Provider provider,
            boolean readFallbackToMinio,
            boolean cosTargetConfigured,
            boolean objectStorageMigrationApplicable) {

        static StorageRuntime from(StorageProperties properties) {
            StorageProperties.Cos cos = properties.getCos();
            boolean cosConfigured = StringUtils.hasText(cos.getRegion())
                    && StringUtils.hasText(cos.getBucket())
                    && StringUtils.hasText(cos.getSecretId())
                    && StringUtils.hasText(cos.getSecretKey());
            return new StorageRuntime(properties.getProvider(), properties.isReadFallbackToMinio(),
                    cosConfigured, cosConfigured);
        }
    }

    public record StaticSafetyGates(
            boolean schedulerEnabled,
            boolean schedulerExecuteEnabled,
            boolean websiteExecuteEnabled,
            boolean articleArchiveExecuteEnabled,
            boolean articlePurgeExecuteEnabled,
            boolean pollExecuteEnabled,
            boolean contentUrlRewriteExecuteEnabled,
            boolean objectStorageMigrationExecuteEnabled,
            boolean executePromotionEnabled,
            boolean dynamicEnableWriteEnabled,
            boolean detailCleanupEnabled) {

        static StaticSafetyGates from(DataRetentionProperties properties, StorageProperties storageProperties) {
            return new StaticSafetyGates(
                    properties.getScheduler().isEnabled(),
                    properties.getScheduler().isExecuteEnabled(),
                    properties.getWebsitePublishedCleanup().isExecuteEnabled(),
                    properties.getArticleArchive().isExecuteEnabled(),
                    properties.getArticlePurge().isExecuteEnabled(),
                    properties.getPollResults().isExecuteEnabled(),
                    properties.getContentUrlRewrite().isExecuteEnabled(),
                    storageProperties.getMigration().isExecuteEnabled(),
                    properties.getExecutePromotion().isEnabled(),
                    properties.getDynamicSwitch().isEnableWriteEnabled(),
                    properties.getDetailCleanup().isEnabled());
        }

        boolean allMutationGatesClosed() {
            return !schedulerExecuteEnabled
                    && !websiteExecuteEnabled
                    && !articleArchiveExecuteEnabled
                    && !articlePurgeExecuteEnabled
                    && !pollExecuteEnabled
                    && !contentUrlRewriteExecuteEnabled
                    && !objectStorageMigrationExecuteEnabled
                    && !executePromotionEnabled
                    && !dynamicEnableWriteEnabled
                    && !detailCleanupEnabled;
        }
    }
}
