package com.huanjing.geo.module.extension.dto;

public record FillTokenIssueResponse(String fillToken, long expiresAt, String nonce) {
}
