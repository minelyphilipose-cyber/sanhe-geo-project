package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.extension.entity.LocalAgentRuntimeStatus;

import java.time.LocalDateTime;

public record LocalAgentRuntimeStatusVO(
        Long id,
        String machineId,
        String activeProfile,
        Long sessionId,
        Long operatorId,
        String helperVersion,
        String protocolVersion,
        Boolean adspowerApiOk,
        Integer runningTaskCount,
        Integer capacity,
        String runtimeState,
        String resourceMetricsJson,
        LocalDateTime lastCleanupAt,
        String helperBootId,
        Long policyVersion,
        LocalDateTime lastSeenAt
) {
    public static LocalAgentRuntimeStatusVO from(LocalAgentRuntimeStatus row) {
        if (row == null) {
            return null;
        }
        return new LocalAgentRuntimeStatusVO(
                row.getId(),
                row.getMachineId(),
                row.getActiveProfile(),
                row.getSessionId(),
                row.getOperatorId(),
                row.getHelperVersion(),
                row.getProtocolVersion(),
                row.getAdspowerApiOk(),
                row.getRunningTaskCount(),
                row.getCapacity(),
                row.getRuntimeState(),
                row.getResourceMetricsJson(),
                row.getLastCleanupAt(),
                row.getHelperBootId(),
                row.getPolicyVersion(),
                row.getLastSeenAt()
        );
    }
}
