package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

@Data
public class SuperAdminWorkbenchOverviewVO {
    private Long totalUserCount;
    private Long activeUserCount;
    private Long totalCompanyCount;
    private Long totalProjectCount;
    private Long nullOwnerCompanyCount;
    private Long deprecatedEffectivePermissionCount;
    private Long openSystemAlertCount;
}
