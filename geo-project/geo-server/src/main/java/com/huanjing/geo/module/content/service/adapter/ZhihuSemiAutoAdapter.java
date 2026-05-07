package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ZhihuSemiAutoAdapter implements SemiAutoSelfMediaAdapter {
    public static final String PLATFORM = "zhihu";

    private final SemiAutoPlatformProperties platformProperties;
    private final MarkdownToHtmlRenderer markdownToHtmlRenderer;

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformFillProfile fillProfile() {
        return platformProperties.profile(PLATFORM);
    }

    @Override
    public MarkdownToHtmlRenderer markdownToHtmlRenderer() {
        return markdownToHtmlRenderer;
    }
}
