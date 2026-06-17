package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkbenchRiskGroupVO {
    private String customerName;
    private String brandName;
    private Long riskCount;
    private Long highSeverityCount;
    private String latestMessage;
    private List<WorkbenchTodoVO> todos;
}
