package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.SearchStatus;
import com.huanjing.geo.module.dispatch.websearch.model.SearchEvidence;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;

import java.util.List;
import java.util.Map;

public record ModelDiagnosticProviderResult(String providerRequestId,
                                            String responseModelId,
                                            String answer,
                                            int httpStatus,
                                            SearchStatus searchStatus,
                                            List<SearchEvidence> searchEvidence,
                                            List<WebSearchSource> sources,
                                            List<WebSearchCitation> citations,
                                            Map<String, Object> usage,
                                            String finishReason,
                                            String sanitizedRequest,
                                            String sanitizedResponse,
                                            long durationMs) {
    public ModelDiagnosticProviderResult {
        searchStatus = searchStatus == null ? SearchStatus.NOT_CONFIRMED : searchStatus;
        searchEvidence = searchEvidence == null ? List.of() : List.copyOf(searchEvidence);
        sources = sources == null ? List.of() : List.copyOf(sources);
        citations = citations == null ? List.of() : List.copyOf(citations);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }
}
