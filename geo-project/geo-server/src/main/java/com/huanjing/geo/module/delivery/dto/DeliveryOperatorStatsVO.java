package com.huanjing.geo.module.delivery.dto;

import lombok.Data;

@Data
public class DeliveryOperatorStatsVO {
    private Long operatorId;
    private String operatorName;
    private Long customerCount;
    private Long activeProjectCount;
    private Long highRiskProjectCount;
    private Long monthlyReportCount;
    private Long monthlyArticleCount;
    private Long openExceptionCount;
    private Long failedDispatchTaskCount;
}
