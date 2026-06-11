package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSelfMediaScheduleBatchVO {
    private Long id;
    private Long projectId;
    private Long brandId;
    private Long companyId;
    private String targetMonth;
    private String triggerMode;
    private String status;
    private String scheduleStrategy;
    private Integer articleCount;
    private Integer accountCount;
    private Integer plannedCount;
    private Integer createdCount;
    private Integer rejectedCount;
    private String generationBatchIds;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectSelfMediaScheduleBatchVO from(ProjectSelfMediaScheduleBatch row) {
        if (row == null) {
            return null;
        }
        ProjectSelfMediaScheduleBatchVO vo = new ProjectSelfMediaScheduleBatchVO();
        vo.setId(row.getId());
        vo.setProjectId(row.getProjectId());
        vo.setBrandId(row.getBrandId());
        vo.setCompanyId(row.getCompanyId());
        vo.setTargetMonth(row.getTargetMonth());
        vo.setTriggerMode(row.getTriggerMode());
        vo.setStatus(row.getStatus());
        vo.setScheduleStrategy(row.getScheduleStrategy());
        vo.setArticleCount(row.getArticleCount());
        vo.setAccountCount(row.getAccountCount());
        vo.setPlannedCount(row.getPlannedCount());
        vo.setCreatedCount(row.getCreatedCount());
        vo.setRejectedCount(row.getRejectedCount());
        vo.setGenerationBatchIds(row.getGenerationBatchIds());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }
}
