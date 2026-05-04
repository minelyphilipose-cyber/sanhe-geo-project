package com.huanjing.geo.module.presale.dto.response;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LlmPromptQuestionGenerateVO {
    private Integer requestedTotal;
    private Integer generatedTotal;
    private Integer missingTotal;
    private Map<PresalePromptCategoryCode, Integer> requestedCategoryCounts;
    private Map<PresalePromptCategoryCode, Integer> generatedCategoryCounts;
    private Map<PresalePromptCategoryCode, Integer> missingCategoryCounts;
    private List<LlmPromptQuestionDraftVO> questions;
    private List<String> warnings;
}
