package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ThirdPartySubjectPoolDtos {
    private ThirdPartySubjectPoolDtos() {
    }

    public record SuggestRequest(
            List<String> coverableIndustries,
            String mode
    ) {
    }

    public record SaveRequest(
            List<String> coverableIndustries,
            @NotNull List<SaveItem> subjects
    ) {
    }

    public record SaveItem(
            @NotNull Long brandId,
            String matchSource,
            String matchedIndustry
    ) {
    }
}
