package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @NotBlank
    @Size(max = 64)
    private String displayName;

    private Long partnerId;
    private String phone;
    private String email;
}
