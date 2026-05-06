package com.huanjing.geo.module.dashboard.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectDashboardAdviceRequest {
    private String summary;
    private List<String> highlights;
    private List<String> improvementDirections;
    private List<String> nextActions;
}
