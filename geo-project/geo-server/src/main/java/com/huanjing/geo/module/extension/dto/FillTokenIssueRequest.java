package com.huanjing.geo.module.extension.dto;

public record FillTokenIssueRequest(
        Long brandId,
        Long accountId,
        Long taskTargetId,
        String extensionVersion,
        String platform
) {
}
