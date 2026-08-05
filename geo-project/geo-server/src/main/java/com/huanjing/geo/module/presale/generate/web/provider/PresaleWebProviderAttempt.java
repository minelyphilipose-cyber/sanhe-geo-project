package com.huanjing.geo.module.presale.generate.web.provider;

import com.huanjing.geo.module.dispatch.websearch.model.WebSearchResponse;

public record PresaleWebProviderAttempt(WebSearchResponse response,
                                        long durationMs) {
}
