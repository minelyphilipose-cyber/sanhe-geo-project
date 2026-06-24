package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EntityJudgeBudgetConfigVO {
    private Long id;
    private String scopeType;
    private Long projectId;
    private Boolean enabled;
    private Integer dailyCallLimit;
    private Integer monthlyCallLimit;
    private BigDecimal dailyEstimatedCostLimit;
    private BigDecimal monthlyEstimatedCostLimit;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
