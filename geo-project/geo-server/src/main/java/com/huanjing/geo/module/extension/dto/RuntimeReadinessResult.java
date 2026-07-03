package com.huanjing.geo.module.extension.dto;

import java.util.List;

public record RuntimeReadinessResult(
        boolean ready,
        List<String> blockedReasons,
        Long extensionRuntimeStatusId,
        Long localAgentRuntimeStatusId,
        Integer retryAfterSeconds
) {
    public static RuntimeReadinessResult ready(Long extensionRuntimeStatusId,
                                               Long localAgentRuntimeStatusId) {
        return new RuntimeReadinessResult(true, List.of(), extensionRuntimeStatusId, localAgentRuntimeStatusId, null);
    }

    public static RuntimeReadinessResult blocked(List<String> blockedReasons,
                                                 Long extensionRuntimeStatusId,
                                                 Long localAgentRuntimeStatusId,
                                                 Integer retryAfterSeconds) {
        return new RuntimeReadinessResult(false, List.copyOf(blockedReasons), extensionRuntimeStatusId, localAgentRuntimeStatusId, retryAfterSeconds);
    }
}
