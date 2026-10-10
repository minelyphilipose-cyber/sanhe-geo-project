package com.huanjing.geo.module.content.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectSelfMediaPlanningTaskVO {
    private Long id;
    private Long projectId;
    private String targetMonth;
    private String status;
    private Boolean supplementExistingBatch;
    private SelfMediaPublishAutoScheduleResponse result;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
