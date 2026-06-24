package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectCompetitorConfigVO {
    private Long id;
    private Long projectId;
    private String competitorName;
    private List<String> aliases = new ArrayList<>();
    private String advantages;
    private String disadvantages;
    private Integer displayOrder;
    private String status;
    private String qaStatus;
    private LocalDateTime qaCheckedAt;
    private Integer configVersion;
    private LocalDateTime updatedAt;
}
