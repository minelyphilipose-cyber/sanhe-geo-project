package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartnerAdjustRequest {
    @NotNull
    private Long amount;
    private String remark;
}
