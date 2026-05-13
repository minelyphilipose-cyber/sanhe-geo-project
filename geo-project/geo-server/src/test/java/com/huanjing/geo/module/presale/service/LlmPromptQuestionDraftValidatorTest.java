package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionDraftRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionPlanRequest;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmPromptQuestionDraftValidatorTest {

    private final LlmPromptQuestionDraftValidator validator = new LlmPromptQuestionDraftValidator();

    @Test
    void validate_rejectsComparisonWithoutCompetitorAndNonComparisonWithCompetitor() {
        LlmPromptQuestionPlanRequest plan = plan(Map.of(
                PresalePromptCategoryCode.RECOMMENDATION, 1,
                PresalePromptCategoryCode.COMPARISON, 1,
                PresalePromptCategoryCode.COGNITIVE, 3
        ));
        List<LlmPromptQuestionDraftRequest> questions = List.of(
                question(PresalePromptCategoryCode.RECOMMENDATION, "海底捞和 {competitor} 哪个好?"),
                question(PresalePromptCategoryCode.COMPARISON, "海底捞有什么优势?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞品牌怎么样?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞服务口碑如何?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞在火锅行业知名度如何?")
        );

        List<LlmPromptQuestionDraftValidator.ValidationError> errors = validator.validate(plan, questions);

        assertFalse(errors.isEmpty());
        assertEquals(2, errors.stream().filter(e -> "promptContent".equals(e.field())).count());
    }

    @Test
    void buildSnapshots_mapsCodeToChineseCategoryAndLlmSource() {
        LlmPromptQuestionPlanRequest plan = plan(Map.of(
                PresalePromptCategoryCode.RECOMMENDATION, 1,
                PresalePromptCategoryCode.COMPARISON, 1,
                PresalePromptCategoryCode.COGNITIVE, 3
        ));
        List<LlmPromptQuestionDraftRequest> questions = List.of(
                question(PresalePromptCategoryCode.RECOMMENDATION, "北京火锅店哪家适合家庭聚餐?"),
                question(PresalePromptCategoryCode.COMPARISON, "海底捞和 {competitor} 相比如何?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞品牌怎么样?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞服务口碑如何?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞在火锅行业知名度如何?")
        );

        List<PresaleReportVersionPromptTemplate> snapshots = validator.validateAndBuildSnapshots(
                plan, questions, 1L, 2L, LocalDateTime.now());

        assertEquals("llm", snapshots.get(0).getSourceType());
        assertEquals("推荐型", snapshots.get(0).getCategory());
        assertEquals("LLM_RECOMMENDATION_001", snapshots.get(0).getSourcePromptCode());
        assertEquals(0, snapshots.get(0).getIsUserAdded());
        assertEquals("LLM_COMPARISON_001", snapshots.get(1).getSourcePromptCode());
    }

    @Test
    void validate_rejectsNonCompetitorPlaceholders() {
        LlmPromptQuestionPlanRequest plan = plan(Map.of(
                PresalePromptCategoryCode.RECOMMENDATION, 1,
                PresalePromptCategoryCode.COMPARISON, 1,
                PresalePromptCategoryCode.COGNITIVE, 3
        ));
        List<LlmPromptQuestionDraftRequest> questions = List.of(
                question(PresalePromptCategoryCode.RECOMMENDATION, "{brand} 在北京火锅里算推荐吗?"),
                question(PresalePromptCategoryCode.COMPARISON, "海底捞和 {competitor} 哪个售后更靠谱?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞品牌怎么样?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞服务口碑如何?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞在火锅行业知名度如何?")
        );

        List<LlmPromptQuestionDraftValidator.ValidationError> errors = validator.validate(plan, questions);

        assertFalse(errors.isEmpty());
        assertEquals(1, errors.stream()
                .filter(e -> e.message().contains("除 {competitor} 外不能包含占位符"))
                .count());
    }

    @Test
    void validate_rejectsCognitiveCountBelowMinimum() {
        LlmPromptQuestionPlanRequest plan = plan(Map.of(
                PresalePromptCategoryCode.RECOMMENDATION, 1,
                PresalePromptCategoryCode.COMPARISON, 1,
                PresalePromptCategoryCode.COGNITIVE, 2
        ));
        List<LlmPromptQuestionDraftRequest> questions = List.of(
                question(PresalePromptCategoryCode.RECOMMENDATION, "北京火锅店哪家适合家庭聚餐?"),
                question(PresalePromptCategoryCode.COMPARISON, "海底捞和 {competitor} 相比如何?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞品牌怎么样?"),
                question(PresalePromptCategoryCode.COGNITIVE, "海底捞服务口碑如何?")
        );

        List<LlmPromptQuestionDraftValidator.ValidationError> errors = validator.validate(plan, questions);

        assertFalse(errors.isEmpty());
        assertEquals(1, errors.stream()
                .filter(e -> "COGNITIVE".equals(e.field()) && e.message().contains("至少 3 条"))
                .count());
    }

    @Test
    void normalizeExistingQuestions_rejectsTooManyItems() {
        List<LlmPromptQuestionDraftRequest> input = java.util.stream.IntStream.range(0, 81)
                .mapToObj(i -> question(PresalePromptCategoryCode.RECOMMENDATION, "海底捞口碑怎么样?" + i))
                .toList();

        assertThrows(BizException.class, () -> validator.normalizeExistingQuestions(input));
    }

    private static LlmPromptQuestionPlanRequest plan(Map<PresalePromptCategoryCode, Integer> counts) {
        Map<PresalePromptCategoryCode, Integer> normalized = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            normalized.put(code, 0);
        }
        normalized.putAll(counts);
        LlmPromptQuestionPlanRequest plan = new LlmPromptQuestionPlanRequest();
        plan.setCategoryCounts(normalized);
        plan.setTotalCount(normalized.values().stream().mapToInt(Integer::intValue).sum());
        return plan;
    }

    private static LlmPromptQuestionDraftRequest question(PresalePromptCategoryCode category, String content) {
        LlmPromptQuestionDraftRequest question = new LlmPromptQuestionDraftRequest();
        question.setCategoryCode(category);
        question.setPromptContent(content);
        return question;
    }
}
