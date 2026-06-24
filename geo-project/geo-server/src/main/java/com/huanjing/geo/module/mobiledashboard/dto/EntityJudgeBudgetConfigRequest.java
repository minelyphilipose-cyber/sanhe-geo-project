package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EntityJudgeBudgetConfigRequest {
    private Boolean enabled = true;
    private Integer dailyCallLimit;
    private Integer monthlyCallLimit;
    private BigDecimal dailyEstimatedCostLimit;
    private BigDecimal monthlyEstimatedCostLimit;
}
