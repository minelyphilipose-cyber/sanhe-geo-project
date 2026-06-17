package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

import java.util.List;

@Data
public class OperatorWorkbenchOverviewVO {
    private Long customerCount;
    private Long brandCount;
    private Long projectCount;
    private Long activeProjectCount;
    private Long highRiskProjectCount;
    private Long monthlyReportCount;
    private Long monthlyArticleCount;

    private Long failedDistributionTaskCount;
    private Long retryDistributionTaskCount;
    private Long semiAutoTaskCount;
    private Long inFlightExtensionTaskCount;
    private Long completedDistributionTaskCount;

    private Long openTodoCount;
    private Long highSeverityTodoCount;
    private List<WorkbenchTodoVO> priorityTodos;
    private List<WorkbenchRiskGroupVO> customerRiskGroups;
}
