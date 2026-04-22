package com.huanjing.geo.module.presale.generate;

import lombok.Data;

@Data
public class PromptTemplateIntentStatRow {
    private String intentLabel;
    private Integer hasCompetitorVar;
    private Integer templateCount;
}

