package com.huanjing.geo.module.mobiledashboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileDashboardSessionRequest {
    @NotBlank
    private String shareCode;
}
