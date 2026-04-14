package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionPoolItemVO {
    private Long id;
    private Long projectId;
    private Long versionId;
    private String questionText;
    private String questionType;
    private String priority;
    private Boolean isCore;
    private String contentStrategy;
    private String strategyKeywords;
    private String strategySuggestedType;
    private LocalDateTime strategyGeneratedAt;
    private String strategyStatus;
}
