package com.huanjing.geo.module.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectDashboardAdviceVO {
    private Long id;
    private Long projectId;
    private String summary;
    private List<String> highlights;
    private List<String> improvementDirections;
    private List<String> nextActions;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
