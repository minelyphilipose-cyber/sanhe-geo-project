package com.huanjing.geo.common.llm.measurement;

import lombok.Data;

@Data
public class LlmPlatformLimitRatioRow {
    private String platformCode;
    private Long totalCount;
    private Long limitedCount;
}
