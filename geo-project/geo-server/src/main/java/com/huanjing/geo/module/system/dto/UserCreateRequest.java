package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    @NotBlank
    @Size(max = 64)
    private String displayName;

    @NotBlank
    @Size(max = 64)
    private String roleKey;

    private Long partnerId;
    private String phone;
    private String email;
    private Boolean isActive;
}
