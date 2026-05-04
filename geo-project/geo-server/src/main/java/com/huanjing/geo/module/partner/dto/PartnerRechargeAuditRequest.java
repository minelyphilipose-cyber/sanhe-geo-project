package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerRechargeAuditRequest {
    @NotBlank
    private String action;
    private String rejectReason;
    private String remark;
}
