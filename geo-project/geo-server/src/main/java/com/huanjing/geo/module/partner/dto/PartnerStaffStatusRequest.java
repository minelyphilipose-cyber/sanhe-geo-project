package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartnerStaffStatusRequest {
    @NotNull
    private Boolean isActive;
}
