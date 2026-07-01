package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerStaffCreateRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$")
    private String username;

    @NotBlank
    @Size(max = 80)
    private String displayName;

    @Size(max = 20)
    private String phone;

    @Size(max = 120)
    private String email;
}
