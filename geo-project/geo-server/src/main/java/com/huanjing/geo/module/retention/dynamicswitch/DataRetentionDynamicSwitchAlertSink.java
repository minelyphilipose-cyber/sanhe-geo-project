package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

public interface DataRetentionDynamicSwitchAlertSink {
    void alert(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey, String safeDetail);

    default void resolve(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey) {
    }

    @Slf4j
    @Component
    final class LoggingAlertSink implements DataRetentionDynamicSwitchAlertSink {
        private final DataRetentionDynamicSwitchMetrics metrics;
        private final SystemAlertService systemAlertService;

        public LoggingAlertSink(DataRetentionDynamicSwitchMetrics metrics,
                                SystemAlertService systemAlertService) {
            this.metrics = metrics;
            this.systemAlertService = systemAlertService;
        }

        @Override
        public void alert(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey, String safeDetail) {
            metrics.alert(code, switchKey);
            log.error("retention dynamic-switch alert code={} switchKey={} detail={}",
                    code, switchKey == null ? null : switchKey.databaseValue(), safeDetail);
            try {
                systemAlertService.createOrRefreshRecipientAlert(
                        "DATA_RETENTION_DYNAMIC_SWITCH",
                        severity(code),
                        "data_retention_dynamic_switch",
                        code,
                        Map.of(
                                "code", code,
                                "switchKey", switchKey == null ? "none" : switchKey.databaseValue(),
                                "detail", safeDetail == null ? "none" : safeDetail),
                        null,
                        "super_admin",
                        dedupeKey(code, switchKey));
            } catch (RuntimeException persistenceFailure) {
                log.error("retention dynamic-switch alert persistence failed code={} error={}",
                        code, persistenceFailure.getClass().getSimpleName());
            }
        }

        @Override
        public void resolve(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey) {
            try {
                systemAlertService.resolveOpenByDedupeKey(dedupeKey(code, switchKey), null);
            } catch (RuntimeException persistenceFailure) {
                log.error("retention dynamic-switch alert resolve failed code={} error={}",
                        code, persistenceFailure.getClass().getSimpleName());
            }
        }

        private static String dedupeKey(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey) {
            String normalizedCode = code == null ? "unknown" : code.toLowerCase(Locale.ROOT);
            String normalizedKey = switchKey == null ? "global" : switchKey.databaseValue();
            return "data_retention_dynamic_switch:" + normalizedCode + ":" + normalizedKey;
        }

        private static String severity(String code) {
            if (code == null) return "error";
            return switch (code) {
                case "REDIS_TIME_ROLLBACK", "UNAPPROVED_ENABLE_OBSERVED", "UNAUTHORIZED_TRUE" -> "critical";
                default -> "error";
            };
        }
    }
}
