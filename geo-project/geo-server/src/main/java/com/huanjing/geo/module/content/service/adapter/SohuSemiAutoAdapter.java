package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SohuSemiAutoAdapter implements SemiAutoSelfMediaAdapter {
    private final SemiAutoPlatformProperties platformProperties;
    private final MarkdownToHtmlRenderer markdownToHtmlRenderer;

    @Override
    public String platform() {
        return "sohu";
    }

    @Override
    public PlatformFillProfile fillProfile() {
        return platformProperties.profile(platform());
    }

    @Override
    public MarkdownToHtmlRenderer markdownToHtmlRenderer() {
        return markdownToHtmlRenderer;
    }
}
