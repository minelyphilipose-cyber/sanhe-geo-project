package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ToutiaoSemiAutoAdapter implements SemiAutoSelfMediaAdapter {

    public static final String PLATFORM = "toutiao";
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^)]+)\\)");
    private static final Pattern HTML_IMAGE_BLOCK_PATTERN = Pattern.compile("(?is)(?:<p\\b[^>]*>\\s*)?<img\\b[^>]*>(?:\\s*</p>)?");

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

    @Override
    public String renderContent(String markdown, String title, PlatformFillProfile profile) {
        return SemiAutoSelfMediaAdapter.super.renderContent(stripImageReferences(markdown), title, profile);
    }

    private String stripImageReferences(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return markdown;
        }
        String text = HTML_IMAGE_BLOCK_PATTERN.matcher(markdown).replaceAll("");
        text = MARKDOWN_IMAGE_PATTERN.matcher(text).replaceAll("");
        return text
                .replaceAll("(?m)^[ \\t]+$", "")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }
}
