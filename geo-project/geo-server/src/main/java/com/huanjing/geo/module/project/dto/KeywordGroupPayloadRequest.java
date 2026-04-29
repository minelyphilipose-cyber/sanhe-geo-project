package com.huanjing.geo.module.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KeywordGroupPayloadRequest {
    @NotNull(message = "companyId is required")
    private Long companyId;

    private Long projectId;

    @Size(max = 64, message = "name length must be <= 64")
    private String name;

    @NotBlank(message = "type is required")
    @Size(max = 16, message = "type length must be <= 16")
    private String type;

    @Size(max = 255, message = "remark length must be <= 255")
    private String remark;

    private Boolean areaEnabled;

    @Size(max = 30, message = "functionIndustryTag length must be <= 30")
    private String functionIndustryTag;

    @Valid
    private KeywordGroupColumnsRequest columns;

    private Integer count;

    @Size(max = 32, message = "llmGenerationToken length must be <= 32")
    private String llmGenerationToken;

    @Valid
    private List<LlmQuestionItemDTO> llmQuestions;

    private List<KeywordPreviewItemVO> resultKeywords;
}
