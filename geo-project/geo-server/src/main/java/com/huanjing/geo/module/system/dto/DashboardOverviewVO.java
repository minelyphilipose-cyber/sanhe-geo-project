package com.huanjing.geo.module.system.dto;

import lombok.Data;

@Data
public class DashboardOverviewVO {
    private Long totalCustomers;
    private Long activeProjects;
    private Long totalProjects;
    private Long monthlyReports;
    private Long monthlyDiagnosisReports;
    private Long openAlerts;
    private Long totalPartners;
    private Long monthlyNewCustomers;
    private Long highRiskProjects;
}
