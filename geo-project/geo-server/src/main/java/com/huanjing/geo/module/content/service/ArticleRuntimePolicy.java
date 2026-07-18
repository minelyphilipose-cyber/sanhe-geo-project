package com.huanjing.geo.module.content.service;

public record ArticleRuntimePolicy(
        String channelGroupCode,
        String channelSubCode,
        String perspectiveCode,
        String contactDisclosureMode,
        boolean allowContactInfo
) {
}
