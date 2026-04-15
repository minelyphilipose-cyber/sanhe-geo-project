package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareVerifyRequest {
    @NotBlank
    private String password;
}
