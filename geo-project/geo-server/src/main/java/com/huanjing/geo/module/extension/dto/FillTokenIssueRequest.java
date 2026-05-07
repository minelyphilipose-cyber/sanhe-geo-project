package com.huanjing.geo.module.extension.dto;

import jakarta.validation.constraints.NotNull;

public record FillTokenIssueRequest(
        @NotNull Long brandId,
        @NotNull Long accountId,
        @NotNull Long taskTargetId,
        String extensionVersion,
        String platform
) {
}
