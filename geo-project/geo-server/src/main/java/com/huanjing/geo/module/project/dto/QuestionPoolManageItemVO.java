package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionPoolManageItemVO {
    private Long projectId;
    private String projectName;
    private Integer versionNo;
    private Integer totalQuestions;
    private Integer coreQuestions;
    private String changeReason;
    private Long createdBy;
    private LocalDateTime createdAt;
}
