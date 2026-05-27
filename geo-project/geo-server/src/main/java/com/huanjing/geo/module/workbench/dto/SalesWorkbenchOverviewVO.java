package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

@Data
public class SalesWorkbenchOverviewVO {
    private Long customerCount;
    private Long signedCustomerCount;
    private Long potentialCustomerCount;
    private Long reportCount;
    private Long monthlyReportCount;
    private Long generatingReportCount;
    private Long doneReportCount;
    private Long failedReportCount;
}
