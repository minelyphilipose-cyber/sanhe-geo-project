package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SelfMediaDistributeRequest {
    @NotNull
    private Long selfMediaAccountId;
    private Long coverMaterialId;
    private List<Long> imageMaterialIds;
    private Map<String, Object> platformOptions;
    private String privateStatus;
    private String downloadType;
    @NotBlank
    private String requestId;
}
