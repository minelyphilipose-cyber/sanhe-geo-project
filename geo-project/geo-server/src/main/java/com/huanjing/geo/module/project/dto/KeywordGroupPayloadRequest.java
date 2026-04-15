package com.huanjing.geo.module.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KeywordGroupPayloadRequest {
    @Size(max = 64, message = "name length must be <= 64")
    private String name;

    @NotBlank(message = "type is required")
    @Size(max = 16, message = "type length must be <= 16")
    private String type;

    @Size(max = 255, message = "remark length must be <= 255")
    private String remark;

    @Valid
    private KeywordGroupColumnsRequest columns;

    private Integer count;
}
