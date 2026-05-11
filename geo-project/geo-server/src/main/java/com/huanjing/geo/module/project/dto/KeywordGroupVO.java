package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeywordGroupVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long projectId;
    private String projectName;
    private String packageType;
    private String name;
    private String type;
    private String typeLabel;
    private Boolean legacyType;
    private Boolean areaEnabled;
    private String functionIndustryTag;
    private String remark;
    private KeywordGroupColumnsVO columns;
    private java.util.List<LlmQuestionItemDTO> llmQuestions;
    private Long estimatedKeywordCount;
    private Long savedKeywordCount;
    private Long savedKeywordCountA;
    private Long savedKeywordCountB;
    private Long savedKeywordCountC;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
