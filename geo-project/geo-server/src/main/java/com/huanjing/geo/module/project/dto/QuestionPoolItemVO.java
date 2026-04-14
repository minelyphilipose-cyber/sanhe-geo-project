package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class QuestionPoolItemVO {
    private Long id;
    private Long projectId;
    private Long versionId;
    private String questionText;
    private String questionType;
    private String priority;
    private Boolean isCore;
}
