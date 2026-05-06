package com.huanjing.geo.module.presale.dto.request;

import lombok.Data;

@Data
public class PresalePromptTraceQueryRequest {
    private Integer current = 1;
    private Integer size = 20;
    private String platformCode;
    private Integer batchNo;
    private String category;
    private String keyword;
    /** SUCCESS / ANALYZE_FAILED / QUERY_FAILED. */
    private String status;
}
