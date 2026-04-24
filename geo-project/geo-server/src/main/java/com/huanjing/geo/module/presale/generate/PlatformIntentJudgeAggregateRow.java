package com.huanjing.geo.module.presale.generate;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlatformIntentJudgeAggregateRow {
    private String platformCode;
    private String category;
    private BigDecimal cellScore;
    /**
     * 对比型专用站队方向:target / tie / competitor / null。
     * 认知型记录的 stance 字段一定为 null,消费方不要对认知型判断此字段。
     */
    private String stance;
    private Integer sampleCount;
}
