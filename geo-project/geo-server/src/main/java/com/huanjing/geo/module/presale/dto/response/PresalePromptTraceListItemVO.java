package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresalePromptTraceListItemVO {
    private Long promptResultId;
    private Long reportId;
    private Long versionId;
    private Integer versionNo;
    private Integer batchNo;
    private String category;
    private String platformCode;
    private String platformName;
    private String traceStatus;
    private String traceStatusText;
    private String requestPromptContent;
    private String queryAnswerBrief;
    private String queryModelName;
    private String analyzeModelName;
    private Integer totalDurationMs;
}
