package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.dispatch.websearch.model.SearchEvidence;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchCitation;
import com.huanjing.geo.module.dispatch.websearch.model.WebSearchSource;

import java.util.List;
import java.util.Map;

public record PresaleSearchEvidence(
        String queryContractVersion,
        String reportPlatformCode,
        Long webConfigId,
        Long webConfigVersion,
        String companionPlatformCode,
        String integrationType,
        String modelId,
        boolean searchTriggered,
        String searchStatus,
        PresaleEvidenceLevel evidenceLevel,
        String failureCode,
        List<String> providerRequestIds,
        int physicalCallCount,
        Integer promptTokens,
        Integer completionTokens,
        Map<String, Object> usage,
        List<SearchEvidence> searchEvidence,
        List<WebSearchSource> sources,
        List<WebSearchCitation> citations) {

    public static final String CONTRACT_VERSION = "WEB_SEARCH_V1";

    public PresaleSearchEvidence {
        providerRequestIds = providerRequestIds == null ? List.of() : List.copyOf(providerRequestIds);
        usage = usage == null ? Map.of() : Map.copyOf(usage);
        searchEvidence = searchEvidence == null ? List.of() : List.copyOf(searchEvidence);
        sources = sources == null ? List.of() : List.copyOf(sources);
        citations = citations == null ? List.of() : List.copyOf(citations);
        evidenceLevel = evidenceLevel == null ? PresaleEvidenceLevel.NONE : evidenceLevel;
    }
}
