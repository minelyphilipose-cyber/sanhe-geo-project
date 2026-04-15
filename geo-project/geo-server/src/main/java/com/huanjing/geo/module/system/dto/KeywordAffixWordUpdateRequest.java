package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KeywordAffixWordUpdateRequest {
    @Size(max = 16, message = "type length must be <= 16")
    private String type;

    @NotBlank(message = "affixKind is required")
    @Size(max = 16, message = "affixKind length must be <= 16")
    private String affixKind;

    @NotBlank(message = "wordText is required")
    @Size(max = 64, message = "wordText length must be <= 64")
    private String wordText;

    private Integer sortOrder;
}
