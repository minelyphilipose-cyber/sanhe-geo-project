package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SelfMediaAccountManageRequest(
        @NotBlank
        @Pattern(regexp = "toutiao|zhihu", message = "platform must be toutiao or zhihu")
        String platform,

        @NotBlank
        @Size(max = 128)
        String accountName,

        @Size(max = 128)
        String platformAccountId,

        @Pattern(regexp = "active|disabled", message = "status must be active or disabled")
        String status
) {
}
