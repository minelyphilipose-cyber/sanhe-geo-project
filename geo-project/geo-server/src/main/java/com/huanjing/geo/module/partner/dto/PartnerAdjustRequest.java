package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerAdjustRequest {
    @NotNull
    private BigDecimal amount;
    private String offlineReference;
    private String remark;
}
