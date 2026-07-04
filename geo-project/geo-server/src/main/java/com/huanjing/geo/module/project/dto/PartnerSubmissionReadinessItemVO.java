package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class PartnerSubmissionReadinessItemVO {
    private String key;
    private String category;
    private String title;
    private String description;
    private Boolean ready;
    private String severity;
    private String actionText;
    private Long projectId;
    private String projectName;
}
