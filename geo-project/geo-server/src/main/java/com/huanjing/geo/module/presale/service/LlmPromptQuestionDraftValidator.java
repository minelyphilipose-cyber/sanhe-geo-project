package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionDraftRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionPlanRequest;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmPromptQuestionDraftValidator {

    public static final int MAX_TOTAL_COUNT = 40;
    public static final int MAX_CATEGORY_COUNT = 30;
    public static final int MIN_COGNITIVE_COUNT = 3;
    public static final int MAX_PROMPT_CONTENT_LENGTH = 1000;
    public static final int MAX_EXISTING_QUESTIONS = 80;
    private static final String COMPETITOR_VAR = "{competitor}";
    private static final Set<String> ALLOWED_VARIABLES = Set.of(
            "{competitor}"
    );
    private static final Pattern BRACED_TOKEN_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern VALID_TOKEN_FORMAT = Pattern.compile("\\{[a-z_]+\\}");

    public List<PresaleReportVersionPromptTemplate> validateAndBuildSnapshots(
            LlmPromptQuestionPlanRequest plan,
            List<LlmPromptQuestionDraftRequest> questions,
            Long reportId,
            Long reportVersionId,
            LocalDateTime createdAt) {
        List<ValidationError> errors = validate(plan, questions);
        if (!errors.isEmpty()) {
            throw new BizException(
                    400,
                    "LLM prompt question validation failed",
                    200,
                    Map.of("errors", errors)
            );
        }

        Map<PresalePromptCategoryCode, Integer> seqByCategory = new EnumMap<>(PresalePromptCategoryCode.class);
        List<PresaleReportVersionPromptTemplate> snapshots = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            LlmPromptQuestionDraftRequest question = questions.get(i);
            PresalePromptCategoryCode category = question.getCategoryCode();
            int seq = seqByCategory.merge(category, 1, Integer::sum);

            PresaleReportVersionPromptTemplate row = new PresaleReportVersionPromptTemplate();
            row.setReportId(reportId);
            row.setReportVersionId(reportVersionId);
            row.setSourceTemplateId(null);
            row.setSourcePromptCode(buildSourcePromptCode(category, seq));
            row.setSourceTemplateVersion("llm_generated");
            row.setSourceType("llm");
            // TODO: migrate presale_report_version_prompt_template.category to category code;
            // keep Chinese display name only in presentation layer.
            row.setCategory(category.getDisplayName());
            row.setBusinessValue(category.getDefaultBusinessValue());
            row.setPromptContent(question.getPromptContent().trim());
            row.setHasCompetitorVar(question.getPromptContent().contains(COMPETITOR_VAR) ? 1 : 0);
            row.setSortOrderInVersion(i + 1);
            row.setRemark(null);
            row.setIsUserAdded(0);
            row.setCreatedAt(createdAt);
            snapshots.add(row);
        }
        return snapshots;
    }

    public List<ValidationError> validate(LlmPromptQuestionPlanRequest plan,
                                          List<LlmPromptQuestionDraftRequest> questions) {
        List<ValidationError> errors = new ArrayList<>();
        if (plan == null) {
            errors.add(new ValidationError(null, "llmQuestionPlan", "LLM 问题数量配置不能为空"));
            return errors;
        }
        int totalCount = plan.getTotalCount() == null ? -1 : plan.getTotalCount();
        if (totalCount <= 0 || totalCount > MAX_TOTAL_COUNT) {
            errors.add(new ValidationError(null, "totalCount", "总问题数必须在 1-" + MAX_TOTAL_COUNT + " 之间"));
        }
        Map<PresalePromptCategoryCode, Integer> categoryCounts = normalizeCategoryCounts(plan.getCategoryCounts());
        int sum = 0;
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            int count = categoryCounts.getOrDefault(code, 0);
            if (count < 0 || count > MAX_CATEGORY_COUNT) {
                errors.add(new ValidationError(null, code.name(), "单分类数量必须在 0-" + MAX_CATEGORY_COUNT + " 之间"));
            }
            sum += Math.max(count, 0);
        }
        if (sum != totalCount) {
            errors.add(new ValidationError(null, "categoryCounts", "各类型数量之和必须等于总问题数"));
        }
        if (categoryCounts.getOrDefault(PresalePromptCategoryCode.COMPARISON, 0) <= 0) {
            errors.add(new ValidationError(null, "COMPARISON", "对比型问题数量必须大于 0"));
        }
        if (categoryCounts.getOrDefault(PresalePromptCategoryCode.COGNITIVE, 0) < MIN_COGNITIVE_COUNT) {
            errors.add(new ValidationError(null, "COGNITIVE", "认知型问题数量必须至少 " + MIN_COGNITIVE_COUNT + " 条"));
        }
        if (questions == null || questions.isEmpty()) {
            errors.add(new ValidationError(null, "llmPromptQuestions", "LLM 问题不能为空"));
            return errors;
        }
        if (questions.size() != totalCount) {
            errors.add(new ValidationError(null, "llmPromptQuestions", "LLM 问题数量必须等于总问题数"));
        }

        Map<PresalePromptCategoryCode, Integer> actualCounts = new EnumMap<>(PresalePromptCategoryCode.class);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            LlmPromptQuestionDraftRequest question = questions.get(i);
            validateOne(i, question, errors, seen);
            if (question != null && question.getCategoryCode() != null) {
                actualCounts.merge(question.getCategoryCode(), 1, Integer::sum);
            }
        }
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            int expected = categoryCounts.getOrDefault(code, 0);
            int actual = actualCounts.getOrDefault(code, 0);
            if (actual != expected) {
                errors.add(new ValidationError(null, code.name(), code.getDisplayName()
                        + "问题数量必须为 " + expected + " 条，当前为 " + actual + " 条"));
            }
        }
        return errors;
    }

    public List<LlmPromptQuestionDraftRequest> normalizeExistingQuestions(List<LlmPromptQuestionDraftRequest> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        if (input.size() > MAX_EXISTING_QUESTIONS) {
            throw new BizException(400, "existingQuestions 最多 " + MAX_EXISTING_QUESTIONS + " 条");
        }
        List<LlmPromptQuestionDraftRequest> normalized = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            LlmPromptQuestionDraftRequest item = input.get(i);
            if (item == null || item.getCategoryCode() == null || !StringUtils.hasText(item.getPromptContent())) {
                continue;
            }
            String text = item.getPromptContent().trim();
            if (text.length() > MAX_PROMPT_CONTENT_LENGTH) {
                throw new BizException(400, "existingQuestions[" + i + "] 最多 " + MAX_PROMPT_CONTENT_LENGTH + " 字");
            }
            LlmPromptQuestionDraftRequest copy = new LlmPromptQuestionDraftRequest();
            copy.setCategoryCode(item.getCategoryCode());
            copy.setPromptContent(text);
            normalized.add(copy);
        }
        return normalized;
    }

    public List<ValidationError> validateQuestionOnly(LlmPromptQuestionDraftRequest question) {
        List<ValidationError> errors = new ArrayList<>();
        validateOne(0, question, errors, new HashSet<>());
        return errors;
    }

    public String normalizeQuestionText(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public String dedupKey(PresalePromptCategoryCode categoryCode, String promptContent) {
        return (categoryCode == null ? "" : categoryCode.name()) + "\n"
                + normalizeQuestionText(promptContent).toLowerCase();
    }

    private void validateOne(int index,
                             LlmPromptQuestionDraftRequest question,
                             List<ValidationError> errors,
                             Set<String> seen) {
        if (question == null) {
            errors.add(new ValidationError(index, "llmPromptQuestions", "问题不能为空"));
            return;
        }
        PresalePromptCategoryCode category = question.getCategoryCode();
        if (category == null) {
            errors.add(new ValidationError(index, "categoryCode", "问题类型不能为空"));
            return;
        }
        String promptContent = normalizeQuestionText(question.getPromptContent());
        if (!StringUtils.hasText(promptContent)) {
            errors.add(new ValidationError(index, "promptContent", "Prompt 内容不能为空"));
            return;
        }
        if (promptContent.length() > MAX_PROMPT_CONTENT_LENGTH) {
            errors.add(new ValidationError(index, "promptContent", "Prompt 内容最多 " + MAX_PROMPT_CONTENT_LENGTH + " 字"));
        }
        seen.add(dedupKey(category, promptContent));

        validateVariables(index, promptContent, errors);
        boolean hasCompetitor = promptContent.contains(COMPETITOR_VAR);
        if (category == PresalePromptCategoryCode.COMPARISON && !hasCompetitor) {
            errors.add(new ValidationError(index, "promptContent", "对比型问题必须包含 {competitor}"));
        }
        if (category != PresalePromptCategoryCode.COMPARISON && hasCompetitor) {
            errors.add(new ValidationError(index, "promptContent", "非对比型问题不能包含 {competitor}"));
        }
    }

    private void validateVariables(int index, String promptContent, List<ValidationError> errors) {
        Matcher matcher = BRACED_TOKEN_PATTERN.matcher(promptContent);
        Set<String> reported = new HashSet<>();
        int tokenCount = 0;
        while (matcher.find()) {
            tokenCount++;
            String token = matcher.group();
            if (!VALID_TOKEN_FORMAT.matcher(token).matches()) {
                if (reported.add(token)) {
                    errors.add(new ValidationError(index, "promptContent", "LLM 问题除 {competitor} 外不能包含占位符: " + token));
                }
                continue;
            }
            if (!ALLOWED_VARIABLES.contains(token) && reported.add(token)) {
                errors.add(new ValidationError(index, "promptContent", "LLM 问题除 {competitor} 外不能包含占位符: " + token));
            }
        }
        if (tokenCount == 0 && (promptContent.contains("{") || promptContent.contains("}"))) {
            errors.add(new ValidationError(index, "promptContent", "LLM 问题除 {competitor} 外不能包含占位符或花括号"));
        }
    }

    private Map<PresalePromptCategoryCode, Integer> normalizeCategoryCounts(Map<PresalePromptCategoryCode, Integer> input) {
        Map<PresalePromptCategoryCode, Integer> result = new EnumMap<>(PresalePromptCategoryCode.class);
        if (input != null) {
            for (Map.Entry<PresalePromptCategoryCode, Integer> entry : input.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey(), entry.getValue() == null ? 0 : entry.getValue());
                }
            }
        }
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            result.putIfAbsent(code, 0);
        }
        return result;
    }

    private String buildSourcePromptCode(PresalePromptCategoryCode category, int seq) {
        return "LLM_" + category.name() + "_" + String.format("%03d", seq);
    }

    public record ValidationError(Integer index, String field, String message) {
    }

}
