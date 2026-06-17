package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

import java.util.List;

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
    private Long openTodoCount;
    private Long highSeverityTodoCount;
    private List<WorkbenchTodoVO> priorityTodos;
    private List<WorkbenchRiskGroupVO> customerRiskGroups;
}
