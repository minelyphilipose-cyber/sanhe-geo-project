package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleItem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSelfMediaSchedulePlanVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long brandId;
    private String brandName;
    private Long selfMediaAccountId;
    private String selfMediaAccountName;
    private String platform;
    private String status;
    private LocalDateTime plannedExecutionAt;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime generationDueAt;
    private String promptTemplateName;
    private Long articleId;
    private Long scheduleId;
    private String generationErrorMessage;
    private String scheduleErrorCode;
    private String scheduleErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectSelfMediaSchedulePlanVO from(ProjectSelfMediaScheduleItem row) {
        ProjectSelfMediaSchedulePlanVO vo = new ProjectSelfMediaSchedulePlanVO();
        vo.setId(row.getId());
        vo.setProjectId(row.getProjectId());
        vo.setBrandId(row.getBrandId());
        vo.setSelfMediaAccountId(row.getSelfMediaAccountId());
        vo.setPlatform(row.getPlatform());
        vo.setStatus(row.getStatus());
        vo.setPlannedExecutionAt(row.getPlannedExecutionAt());
        vo.setPlannedPublishAt(row.getPlannedPublishAt());
        vo.setGenerationDueAt(row.getGenerationDueAt());
        vo.setPromptTemplateName(row.getPromptTemplateName());
        vo.setArticleId(row.getArticleId());
        vo.setScheduleId(row.getScheduleId());
        vo.setGenerationErrorMessage(row.getGenerationErrorMessage());
        vo.setScheduleErrorCode(row.getScheduleErrorCode());
        vo.setScheduleErrorMessage(row.getScheduleErrorMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }
}
