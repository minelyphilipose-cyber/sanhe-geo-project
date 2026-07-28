package com.huanjing.geo.module.dispatch.websearch.model;

import com.huanjing.geo.module.dispatch.websearch.enums.BrandMatchStrength;

import java.time.LocalDateTime;
import java.util.List;

public record WebSearchSource(int searchEventIndex,
                              int rank,
                              String query,
                              String title,
                              String originalUrl,
                              String normalizedUrl,
                              String domain,
                              String media,
                              String snippet,
                              LocalDateTime publishTime,
                              BrandMatchStrength brandMatchStrength,
                              List<String> matchedKeywords) {
    public WebSearchSource {
        brandMatchStrength = brandMatchStrength == null ? BrandMatchStrength.NONE : brandMatchStrength;
        matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
    }
}
