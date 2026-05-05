package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelfMediaDistributeRequest {
    @NotNull
    private Long selfMediaAccountId;
    @NotNull
    private Long coverMaterialId;
    @NotBlank
    private String requestId;
}
