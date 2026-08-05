package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Prompt QUERY 的可展示联网证据。仅暴露产品需要的安全字段。 */
@Data
@Builder
public class PresalePromptTraceEvidenceVO {
    private Boolean webSearch;
    private String queryContractVersion;
    private Boolean searchTriggered;
    private String searchStatus;
    private String searchStatusText;
    private String evidenceLevel;
    private String evidenceLevelText;
    private String failureCode;
    private String notice;
    private List<String> searchQueries;
    private List<SourceView> sources;
    private List<CitationView> citations;

    @Data
    @Builder
    public static class SourceView {
        private Integer index;
        private Integer rank;
        private String title;
        private String url;
        private String domain;
        private String media;
        private String snippet;
        private String publishTime;
        private String query;
    }

    @Data
    @Builder
    public static class CitationView {
        private Integer index;
        private String text;
        private String confidence;
        private String validationStatus;
    }
}
