package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CurrentUserProfileUpdateRequest {
    @NotBlank
    @Size(max = 64)
    private String displayName;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 128)
    private String email;
}
