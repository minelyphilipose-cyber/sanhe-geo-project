package com.huanjing.geo.module.content.service.adapter;

import java.util.List;
import java.util.Map;

public record PlatformFillProfile(
        String platform,
        String publishUrl,
        List<String> cookieDomains,
        List<String> requiredCookieNames,
        Map<String, String> editorSelectors,
        List<String> allowedHtmlTags,
        Map<String, Object> platformOptions
) {
    public PlatformFillProfile {
        cookieDomains = cookieDomains == null ? List.of() : List.copyOf(cookieDomains);
        requiredCookieNames = requiredCookieNames == null ? List.of() : List.copyOf(requiredCookieNames);
        editorSelectors = editorSelectors == null ? Map.of() : Map.copyOf(editorSelectors);
        allowedHtmlTags = allowedHtmlTags == null ? List.of() : List.copyOf(allowedHtmlTags);
        platformOptions = platformOptions == null ? Map.of() : Map.copyOf(platformOptions);
    }
}
