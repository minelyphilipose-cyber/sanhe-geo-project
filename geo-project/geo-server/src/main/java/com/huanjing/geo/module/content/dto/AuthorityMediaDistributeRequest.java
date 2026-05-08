package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthorityMediaDistributeRequest {
    @NotNull
    private Long resourceId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal salingPrice;
    @NotBlank
    private String previewUrl;
    private String publishedAt;
    private String remark;
}
