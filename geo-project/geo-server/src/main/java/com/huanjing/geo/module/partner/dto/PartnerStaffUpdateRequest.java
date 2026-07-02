package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerStaffUpdateRequest {
    @NotBlank
    @Size(max = 80)
    private String displayName;

    @Size(max = 20)
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 120)
    @Email(message = "邮箱格式不正确")
    private String email;
}
