package com.huanjing.geo.module.partner.dto;

import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerKeywordGroupVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long projectId;
    private String projectName;
    private String packageType;
    private String name;
    private String typeLabel;
    private Boolean areaEnabled;
    private String functionIndustryTag;
    private String remark;
    private PartnerKeywordGroupColumnsVO columns;
    private List<LlmQuestionItemDTO> llmQuestions;
    private Long estimatedCoreQuestionCount;
    private Long savedCoreQuestionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
