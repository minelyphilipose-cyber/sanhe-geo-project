package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompanyDeductRequest {
    @NotNull
    private BigDecimal amount;
    @NotBlank
    private String reason;
    private String remark;
}
