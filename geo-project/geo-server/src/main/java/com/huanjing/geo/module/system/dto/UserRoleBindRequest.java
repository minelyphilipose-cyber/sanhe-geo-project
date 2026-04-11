package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRoleBindRequest {
    @NotBlank
    private String roleKey;
}
