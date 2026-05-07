package com.huanjing.geo.module.extension.dto;

public record BindCodeCreateResponse(String code, Long brandId, Long operatorId, long expiresInSeconds) {
}
