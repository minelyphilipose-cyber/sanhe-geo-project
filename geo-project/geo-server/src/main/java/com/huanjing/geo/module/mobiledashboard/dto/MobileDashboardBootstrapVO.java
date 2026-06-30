package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MobileDashboardBootstrapVO {
    private Long projectId;
    private String projectName;
    private String brandName;
    private Map<String, Boolean> availablePages;
    private List<MobileDashboardContentPlatformVO> contentPlatforms = new ArrayList<>();
    private String message;
}
