package com.huanjing.geo.module.content.service.adapter;

import java.util.List;

public record SemiAutoFillTask(
        String platform,
        String publishUrl,
        String title,
        String renderedHtml,
        String coverImageUrl,
        List<String> tags,
        String category,
        PlatformFillProfile profile
) {
    public SemiAutoFillTask {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
