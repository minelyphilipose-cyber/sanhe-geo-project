package com.huanjing.geo.module.delivery.dto;

import lombok.Data;

@Data
public class DeliveryOverviewVO {
    private Long totalCustomers;
    private Long activeProjects;
    private Long highRiskProjects;
    private Long openExceptions;
    private Long failedDispatchTasks;
    private Long monthlyReports;
    private Long monthlyArticles;
    private Long activeOperators;
}
