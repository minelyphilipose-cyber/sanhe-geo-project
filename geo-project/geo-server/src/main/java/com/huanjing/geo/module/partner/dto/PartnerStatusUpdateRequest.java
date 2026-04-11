package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartnerStatusUpdateRequest {
    @NotBlank
    private String status;
}
