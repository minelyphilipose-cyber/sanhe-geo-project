package com.huanjing.geo.module.presale.dto.response;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import lombok.Data;

@Data
public class LlmPromptQuestionDraftVO {
    private PresalePromptCategoryCode categoryCode;
    private String categoryLabel;
    private String promptContent;
    private Boolean hasCompetitorVar;
}
