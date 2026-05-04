package com.huanjing.geo.module.presale.dto.request;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class LlmPromptQuestionPlanRequest {
    @NotNull(message = "totalCount 不能为空")
    private Integer totalCount;

    @NotNull(message = "categoryCounts 不能为空")
    private Map<PresalePromptCategoryCode, Integer> categoryCounts;
}
