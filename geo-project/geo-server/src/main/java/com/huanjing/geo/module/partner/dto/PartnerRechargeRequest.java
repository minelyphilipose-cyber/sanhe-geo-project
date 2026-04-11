package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartnerRechargeRequest {
    @NotNull
    @Min(1)
    private Long amount;
    private String offlineReference;
    private String remark;
}
