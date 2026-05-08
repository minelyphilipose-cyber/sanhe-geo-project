package com.huanjing.geo.module.extension.dto;

public record ExtensionSelfMediaAccountResponse(
        Long accountId,
        String platform,
        String accountName,
        Long brandId,
        String brandName
) {
}
