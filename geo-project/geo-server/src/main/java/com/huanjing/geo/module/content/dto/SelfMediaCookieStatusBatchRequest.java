package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SelfMediaCookieStatusBatchRequest(
        @NotEmpty List<Long> articleIds,
        List<String> platforms
) {
}
