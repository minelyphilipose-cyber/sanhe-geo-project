package com.huanjing.geo.module.presale.dto.response;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import lombok.Data;

import java.util.List;

@Data
public class LlmPromptQuestionDraftVO {
    private PresalePromptCategoryCode categoryCode;
    private String categoryLabel;
    private String promptContent;
    private Boolean hasCompetitorVar;
    private List<String> qualityErrors;
    private List<String> qualityWarnings;
}
