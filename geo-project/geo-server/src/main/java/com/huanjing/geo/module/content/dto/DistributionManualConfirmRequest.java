package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DistributionManualConfirmRequest {
    @NotBlank
    private String publishedUrl;
    private String responsePayload;
}
