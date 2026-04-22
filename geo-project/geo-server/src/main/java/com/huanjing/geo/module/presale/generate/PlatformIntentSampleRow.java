package com.huanjing.geo.module.presale.generate;

import lombok.Data;

/** 平台-意图原子样本(来自 presale_ai_prompt_result × presale_prompt_template)。 */
@Data
public class PlatformIntentSampleRow {
    private String platformCode;
    private String intentLabel;
    private String callStatus;
    private Integer isExcluded;
    private Integer isMentioned;
}
