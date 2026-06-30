package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SelfMediaAccountManageRequest(
        @NotBlank
        @Pattern(regexp = "wechat|douyin|baijiahao|zhihu|xiaohongshu|toutiao|netease|sohu", message = "unsupported self-media platform")
        String platform,

        @NotBlank
        @Size(max = 128)
        String accountName,

        @Size(max = 128)
        String platformAccountId,

        @Pattern(regexp = "personal|enterprise", message = "accountIdentity must be personal or enterprise")
        String accountIdentity,

        @Pattern(regexp = "active|disabled", message = "status must be active or disabled")
        String status
) {
}
