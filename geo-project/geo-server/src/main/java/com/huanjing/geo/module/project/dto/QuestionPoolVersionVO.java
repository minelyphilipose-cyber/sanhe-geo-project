package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionPoolVersionVO {
    private Long id;
    private Long projectId;
    private Integer versionNo;
    private String changeReason;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer totalQuestions;
    private Integer coreQuestions;
    private List<QuestionPoolItemVO> items;
}
