package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

public interface DataRetentionDynamicSwitchAlertSink {
    void alert(String code, DataRetentionDynamicSwitchModels.SwitchKey switchKey, String safeDetail);

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
                systemAlertService.createAlert(
                        "DATA_RETENTION_DYNAMIC_SWITCH",
                        severity(code),
                        "data_retention_dynamic_switch",
                        code,
                        Map.of(
                                "code", code,
                                "switchKey", switchKey == null ? "none" : switchKey.databaseValue(),
                                "detail", safeDetail == null ? "none" : safeDetail));
            } catch (RuntimeException persistenceFailure) {
                log.error("retention dynamic-switch alert persistence failed code={} error={}",
                        code, persistenceFailure.getClass().getSimpleName());
            }
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
