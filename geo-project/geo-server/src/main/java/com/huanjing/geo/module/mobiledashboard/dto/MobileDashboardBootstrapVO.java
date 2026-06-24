package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MobileDashboardBootstrapVO {
    private Long projectId;
    private String projectName;
    private String brandName;
    private Map<String, Boolean> availablePages;
    private String message;
}
