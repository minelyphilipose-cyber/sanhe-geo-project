package com.huanjing.geo.module.content.service;

public record ArticleContentLengthPolicy(
        String requestedLengthCode,
        int targetMinChars,
        int targetMaxChars,
        String source
) {
}
