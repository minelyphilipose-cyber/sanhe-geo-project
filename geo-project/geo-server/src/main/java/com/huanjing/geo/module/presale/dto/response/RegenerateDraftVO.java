package com.huanjing.geo.module.presale.dto.response;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RegenerateDraftVO {
    private Long reportId;
    private String brandName;
    private String industry;
    private String industryRole;
    private String region;
    private String userDemand;
    private String userType;
    private List<String> specifiedCompetitors;
    private String promptSourceMode;
    private List<TemplateQuestion> promptTemplates;
    private LlmQuestionPlan llmQuestionPlan;
    private List<LlmQuestion> llmPromptQuestions;

    @Data
    @Builder
    public static class TemplateQuestion {
        private Long sourceTemplateId;
        private String sourcePromptCode;
        private String promptContent;
    }

    @Data
    @Builder
    public static class LlmQuestionPlan {
        private Integer totalCount;
        private Map<PresalePromptCategoryCode, Integer> categoryCounts;
    }

    @Data
    @Builder
    public static class LlmQuestion {
        private PresalePromptCategoryCode categoryCode;
        private String promptContent;
    }
}
