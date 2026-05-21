package com.huanjing.geo.module.presale.generate;

import lombok.Data;

import java.math.BigDecimal;

/** 单条 prompt 的裁判信号,用于 scene_coverage 明细覆盖判定。 */
@Data
public class PromptJudgeSignalRow {
    private Long promptTemplateId;
    private String platformCode;
    private String category;
    private String judgeStatus;
    private BigDecimal attributeHitRate;
    private String preferredBrand;
}
