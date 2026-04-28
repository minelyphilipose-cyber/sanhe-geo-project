package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KeywordWordItemRequest {
    @NotBlank(message = "wordText is required")
    @Size(max = 64, message = "wordText length must be <= 64")
    private String wordText;

    @Size(max = 16, message = "source length must be <= 16")
    private String source;

    private Integer sortOrder;

    private Boolean isManual;

    private Boolean isTemporary;

    @Size(max = 16, message = "scopeType length must be <= 16")
    private String scopeType;

    private Long scopeId;
}
