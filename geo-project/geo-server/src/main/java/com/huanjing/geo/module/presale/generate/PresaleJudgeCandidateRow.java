package com.huanjing.geo.module.presale.generate;

import lombok.Data;

@Data
public class PresaleJudgeCandidateRow {
    private Long promptResultId;
    private Long versionId;
    private Integer batchNo;
    private String platformCode;
    private Long promptTemplateId;
    private String category;
    private String competitorName;
    private String requestPromptContent;
    private String queryAnswer;
}

