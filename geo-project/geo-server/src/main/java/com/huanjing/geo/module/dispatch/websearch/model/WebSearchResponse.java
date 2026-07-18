package com.huanjing.geo.module.dispatch.websearch.model;

import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;

import java.util.List;
import java.util.Map;

public record WebSearchResponse(String providerRequestId,
                                String requestedModelId,
                                String responseModelId,
                                String answer,
                                SearchStatus searchStatus,
                                boolean generationSkipped,
                                List<SearchEvidence> searchEvidence,
                                List<WebSearchSource> sources,
                                List<WebSearchCitation> citations,
                                Map<String, Object> usage,
                                String finishReason) {
    public WebSearchResponse {
        searchStatus = searchStatus == null ? SearchStatus.NOT_CONFIRMED : searchStatus;
        searchEvidence = searchEvidence == null ? List.of() : List.copyOf(searchEvidence);
        sources = sources == null ? List.of() : List.copyOf(sources);
        citations = citations == null ? List.of() : List.copyOf(citations);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }

    public boolean searchTriggered() {
        return searchStatus.searchActuallyExecuted();
    }
}
