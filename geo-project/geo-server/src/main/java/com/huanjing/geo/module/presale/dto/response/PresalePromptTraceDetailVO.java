package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresalePromptTraceDetailVO {
    private PresalePromptTraceListItemVO summary;
    private String queryPromptContent;
    private String queryRawResponse;
    private String queryCallStatus;
    private String queryFailureReason;
    private Integer queryDurationMs;
    private Boolean queryModelSnapshotInferred;
    private String analyzePromptContent;
    private String analyzeRawResponse;
    private String analyzeCallStatus;
    private String analyzeFailureReason;
    private Integer analyzeDurationMs;
    private Boolean analyzeModelSnapshotInferred;
    private PresalePromptTraceParseViewVO parseView;
}
