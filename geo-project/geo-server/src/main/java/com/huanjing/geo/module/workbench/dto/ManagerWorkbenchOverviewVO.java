package com.huanjing.geo.module.workbench.dto;

import com.huanjing.geo.module.system.dto.SystemAlertTodoVO;
import lombok.Data;

import java.util.List;

@Data
public class ManagerWorkbenchOverviewVO {
    private Long activeUserCount;
    private Long activeOperatorCount;
    private Long permissionCount;
    private Long aiPlatformConfigCount;
    private Long publishSiteCount;
    private Long openSystemAlertCount;
    private Long highSeveritySystemAlertCount;
    private List<SystemAlertTodoVO> latestSystemAlerts;
}
