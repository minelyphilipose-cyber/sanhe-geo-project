package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class LocalAgentClaimDiagnosticRow {
    private long dueCount;
    private long activeEnvironmentCount;
    private long anyBindingCount;
    private long operatorBindingCount;
    private long helperRuntimeCount;
    private long localAgentBindingCount;
}
