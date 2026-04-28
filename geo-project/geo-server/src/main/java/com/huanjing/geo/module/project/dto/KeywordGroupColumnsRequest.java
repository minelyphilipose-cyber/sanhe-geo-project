package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordGroupColumnsRequest {
    private List<KeywordWordItemRequest> areaWords;
    /**
     * Legacy alias kept for old clients during V1.5 rollout. New payloads should use areaWords.
     * TODO V1.6: remove after frontend and external callers fully migrate.
     */
    @Deprecated
    private List<KeywordWordItemRequest> regionWords;
    private List<KeywordWordItemRequest> prefixWords;
    private List<KeywordWordItemRequest> coreWords;
    private List<KeywordWordItemRequest> industryWords;
    private List<KeywordWordItemRequest> suffixWords;
    private List<KeywordWordItemRequest> coreWordsA;
    private List<KeywordWordItemRequest> compareWords;
    private List<KeywordWordItemRequest> coreWordsB;
}
