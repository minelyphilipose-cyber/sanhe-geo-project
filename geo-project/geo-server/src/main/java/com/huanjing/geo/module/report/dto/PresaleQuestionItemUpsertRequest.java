package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PresaleQuestionItemUpsertRequest {
    private Long id;
    @NotBlank
    private String content;
    @NotBlank
    private String questionType;
    private String source;
    private Integer sortOrder;
    private Boolean isActive;
}
