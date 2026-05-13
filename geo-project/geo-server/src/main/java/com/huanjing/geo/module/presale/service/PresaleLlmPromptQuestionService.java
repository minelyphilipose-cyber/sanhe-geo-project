package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionDraftRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionPlanRequest;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionDraftVO;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionGenerateVO;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresaleLlmPromptQuestionService {

    private static final int CONNECT_TIMEOUT_MS = 30_000;

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final LlmInvoker llmInvoker;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final PresaleLlmQuestionRateLimiter rateLimiter;
    private final LlmPromptQuestionDraftValidator validator;

    @Value("${presale.llm-question.meta-prompt:}")
    private String metaPromptOverride;

    public LlmPromptQuestionGenerateVO generate(LlmPromptQuestionGenerateRequest req) {
        currentUserService.ensurePermission("presale.report.create");
        SysUser user = currentUserService.requireCurrentUser();

        LlmPromptQuestionPlanRequest plan = new LlmPromptQuestionPlanRequest();
        plan.setTotalCount(req.getTotalCount());
        plan.setCategoryCounts(req.getCategoryCounts());
        List<LlmPromptQuestionDraftRequest> existing = validator.normalizeExistingQuestions(req.getExistingQuestions());
        validateGenerationPlan(plan, existing);

        Map<PresalePromptCategoryCode, Integer> targetCounts = normalizeCounts(req.getCategoryCounts());
        Map<PresalePromptCategoryCode, Integer> existingCounts = countByCategory(existing);
        Map<PresalePromptCategoryCode, Integer> missingBefore = missingCounts(targetCounts, existingCounts);
        int requestedThisCall = missingBefore.values().stream().mapToInt(Integer::intValue).sum();
        if (requestedThisCall <= 0) {
            return buildResponse(req.getTotalCount(), targetCounts, List.of(), missingBefore, List.of("当前问题数量已满足配置"));
        }

        rateLimiter.acquire(user.getId());
        List<LlmPromptQuestionDraftRequest> generated = invokeAndNormalize(req, existing, missingBefore);
        Map<PresalePromptCategoryCode, Integer> generatedCounts = countByCategory(generated);
        Map<PresalePromptCategoryCode, Integer> afterCounts = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            afterCounts.put(code, existingCounts.getOrDefault(code, 0) + generatedCounts.getOrDefault(code, 0));
        }
        Map<PresalePromptCategoryCode, Integer> missingAfter = missingCounts(targetCounts, afterCounts);

        List<String> warnings = new ArrayList<>();
        int missingTotal = missingAfter.values().stream().mapToInt(Integer::intValue).sum();
        if (missingTotal > 0) {
            warnings.add("已生成 " + generated.size() + " 条，还差 " + missingTotal + " 条，请手动补齐或重新生成");
        }
        return buildResponse(req.getTotalCount(), targetCounts, generated, missingAfter, warnings);
    }

    private void validateGenerationPlan(LlmPromptQuestionPlanRequest plan, List<LlmPromptQuestionDraftRequest> existing) {
        List<LlmPromptQuestionDraftValidator.ValidationError> errors = validatePlanOnly(plan);
        if (!errors.isEmpty()) {
            throw new BizException(400, "LLM prompt question plan invalid", 200, Map.of("errors", errors));
        }
        for (int i = 0; i < existing.size(); i++) {
            List<LlmPromptQuestionDraftValidator.ValidationError> itemErrors = validator.validateQuestionOnly(existing.get(i));
            if (!itemErrors.isEmpty()) {
                throw new BizException(400, "existingQuestions[" + i + "] invalid", 200, Map.of("errors", itemErrors));
            }
        }
        Map<PresalePromptCategoryCode, Integer> targetCounts = normalizeCounts(plan.getCategoryCounts());
        Map<PresalePromptCategoryCode, Integer> existingCounts = countByCategory(existing);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            if (existingCounts.getOrDefault(code, 0) > targetCounts.getOrDefault(code, 0)) {
                throw new BizException(400, code.name() + " 已有问题数量超过目标数量");
            }
        }
    }

    private List<LlmPromptQuestionDraftValidator.ValidationError> validatePlanOnly(LlmPromptQuestionPlanRequest plan) {
        List<LlmPromptQuestionDraftValidator.ValidationError> errors = new ArrayList<>();
        if (plan == null) {
            errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, "llmQuestionPlan", "LLM 问题数量配置不能为空"));
            return errors;
        }
        int totalCount = plan.getTotalCount() == null ? -1 : plan.getTotalCount();
        if (totalCount <= 0 || totalCount > LlmPromptQuestionDraftValidator.MAX_TOTAL_COUNT) {
            errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, "totalCount",
                    "总问题数必须在 1-" + LlmPromptQuestionDraftValidator.MAX_TOTAL_COUNT + " 之间"));
        }
        Map<PresalePromptCategoryCode, Integer> counts = normalizeCounts(plan.getCategoryCounts());
        int sum = 0;
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            int count = counts.getOrDefault(code, 0);
            if (count < 0 || count > LlmPromptQuestionDraftValidator.MAX_CATEGORY_COUNT) {
                errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, code.name(),
                        "单分类数量必须在 0-" + LlmPromptQuestionDraftValidator.MAX_CATEGORY_COUNT + " 之间"));
            }
            sum += Math.max(count, 0);
        }
        if (sum != totalCount) {
            errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, "categoryCounts", "各类型数量之和必须等于总问题数"));
        }
        if (counts.getOrDefault(PresalePromptCategoryCode.COMPARISON, 0) <= 0) {
            errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, "COMPARISON", "对比型问题数量必须大于 0"));
        }
        if (counts.getOrDefault(PresalePromptCategoryCode.COGNITIVE, 0) < LlmPromptQuestionDraftValidator.MIN_COGNITIVE_COUNT) {
            errors.add(new LlmPromptQuestionDraftValidator.ValidationError(null, "COGNITIVE",
                    "认知型问题数量必须至少 " + LlmPromptQuestionDraftValidator.MIN_COGNITIVE_COUNT + " 条"));
        }
        return errors;
    }

    private List<LlmPromptQuestionDraftRequest> invokeAndNormalize(LlmPromptQuestionGenerateRequest req,
                                                                   List<LlmPromptQuestionDraftRequest> existing,
                                                                   Map<PresalePromptCategoryCode, Integer> missingCounts) {
        String rawText;
        try {
            rawText = invokeOnce(renderUserPrompt(req, existing, missingCounts));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LLM question generation request failed", ex);
            throw new BizException(400, "LLM 问题生成失败，请稍后重试");
        }
        List<LlmPromptQuestionDraftRequest> parsed = parseQuestions(rawText);
        Set<String> existingKeys = new LinkedHashSet<>();
        for (LlmPromptQuestionDraftRequest item : existing) {
            existingKeys.add(validator.dedupKey(item.getCategoryCode(), item.getPromptContent()));
        }
        Map<PresalePromptCategoryCode, Integer> acceptedCounts = new EnumMap<>(PresalePromptCategoryCode.class);
        List<LlmPromptQuestionDraftRequest> accepted = new ArrayList<>();
        Set<String> generatedKeys = new LinkedHashSet<>();
        for (LlmPromptQuestionDraftRequest item : parsed) {
            if (item == null || item.getCategoryCode() == null) {
                continue;
            }
            int needed = missingCounts.getOrDefault(item.getCategoryCode(), 0);
            if (acceptedCounts.getOrDefault(item.getCategoryCode(), 0) >= needed) {
                continue;
            }
            item.setPromptContent(validator.normalizeQuestionText(item.getPromptContent()));
            if (isProblemQuestionWithTargetBrand(item, req.getBrandName())) {
                continue;
            }
            if (!validator.validateQuestionOnly(item).isEmpty()) {
                continue;
            }
            String key = validator.dedupKey(item.getCategoryCode(), item.getPromptContent());
            if (existingKeys.contains(key) || !generatedKeys.add(key)) {
                continue;
            }
            accepted.add(item);
            acceptedCounts.merge(item.getCategoryCode(), 1, Integer::sum);
        }
        return accepted;
    }

    private boolean isProblemQuestionWithTargetBrand(LlmPromptQuestionDraftRequest item, String brandName) {
        return item.getCategoryCode() == PresalePromptCategoryCode.PROBLEM
                && StringUtils.hasText(brandName)
                && StringUtils.hasText(item.getPromptContent())
                && item.getPromptContent().contains(brandName.trim());
    }

    private String invokeOnce(String userPrompt) throws Exception {
        List<AiPlatformConfig> configs = requirePlatformConfigs();
        Exception lastError = null;
        boolean quotaFailure = false;
        for (AiPlatformConfig config : configs) {
            try {
                return invokePlatform(config, userPrompt);
            } catch (LlmQuestionProviderException ex) {
                lastError = ex;
                quotaFailure = quotaFailure || ex.isQuotaFailure();
                log.warn("LLM question generation provider failed, platformCode={}, statusCode={}, body={}",
                        ex.platformCode, ex.statusCode, ex.bodySnippet);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("LLM question generation provider failed, platformCode={}, msg={}",
                        config.getPlatformCode(), ex.getMessage());
            }
        }
        if (quotaFailure) {
            throw new BizException(400, "LLM 问题生成失败：AI 平台余额或额度不足，请检查平台账户");
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new BizException(400, "LLM 问题生成失败，请检查 AI 平台配置");
    }

    private String invokePlatform(AiPlatformConfig config, String userPrompt) throws Exception {
        String modelId = config.getLowModelId().trim();
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing API key");
        }

        try {
            LlmInvokeResult response = llmInvoker.invoke(userPrompt, new LlmModelConfig(
                config.getPlatformCode(),
                config.getPlatformName(),
                modelId,
                config.getModelName(),
                config.getApiUrl(),
                apiKey,
                resolveSystemPrompt(),
                0.4D,
                Math.max(CONNECT_TIMEOUT_MS, llmProperties.getConnectTimeoutMs()),
                llmProperties.getRequestTimeoutMs(),
                0,
                Math.max(1, config.getRateLimitQps() == null ? 1 : config.getRateLimitQps()),
                null,
                false
            ));
            return response.responseText();
        } catch (com.huanjing.geo.common.llm.LlmInvokeException ex) {
            throw new LlmQuestionProviderException(
                    config.getPlatformCode(),
                    extractHttpStatus(ex),
                    safeSnippet(ex.getMessage())
            );
        }
    }

    private List<AiPlatformConfig> requirePlatformConfigs() {
        List<AiPlatformConfig> configs = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForPresale, true)
                        .isNotNull(AiPlatformConfig::getLowModelId)
                        .apply("TRIM(low_model_id) <> ''")
                        .orderByAsc(AiPlatformConfig::getPlatformCode)
        );
        if (configs == null || configs.isEmpty()) {
            throw new BizException(400, "LLM 问题生成失败，请检查 AI 平台配置");
        }
        List<AiPlatformConfig> valid = new ArrayList<>();
        for (AiPlatformConfig config : configs) {
            if (config == null || !StringUtils.hasText(config.getApiUrl())) {
                continue;
            }
            if (StringUtils.hasText(config.getLowModelId())) {
                valid.add(config);
            }
        }
        if (valid.isEmpty()) {
            throw new BizException(400, "LLM 问题生成失败，请检查 AI 平台模型配置");
        }
        return valid;
    }

    private String renderUserPrompt(LlmPromptQuestionGenerateRequest req,
                                    List<LlmPromptQuestionDraftRequest> existing,
                                    Map<PresalePromptCategoryCode, Integer> missingCounts) {
        return PresaleLlmPromptQuestionPrompts.USER_PROMPT_TEMPLATE.formatted(
                safe(req.getBrandName()),
                safe(req.getIndustry()),
                safe(req.getIndustryRole()),
                safe(req.getRegion()),
                safe(req.getUserType()),
                safe(req.getUserDemand()),
                formatCounts(missingCounts),
                formatCategoryGuides(missingCounts),
                formatExisting(existing)
        );
    }

    private String resolveSystemPrompt() {
        return StringUtils.hasText(metaPromptOverride)
                ? metaPromptOverride.trim()
                : PresaleLlmPromptQuestionPrompts.SYSTEM_PROMPT;
    }

    private String formatCounts(Map<PresalePromptCategoryCode, Integer> counts) {
        List<String> lines = new ArrayList<>();
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            int count = counts.getOrDefault(code, 0);
            if (count > 0) {
                lines.add("- " + code.name() + "(" + code.getDisplayName() + "): " + count + " 条");
            }
        }
        return String.join("\n", lines);
    }

    private String formatCategoryGuides(Map<PresalePromptCategoryCode, Integer> counts) {
        List<String> lines = new ArrayList<>();
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            if (counts.getOrDefault(code, 0) <= 0) {
                continue;
            }
            lines.add("- " + code.name() + "(" + code.getDisplayName() + "):\n" + code.getGenerationGuide());
        }
        return String.join("\n\n", lines);
    }

    private String formatExisting(List<LlmPromptQuestionDraftRequest> existing) {
        if (existing.isEmpty()) {
            return "- 无";
        }
        List<String> lines = new ArrayList<>();
        for (LlmPromptQuestionDraftRequest item : existing) {
            lines.add("- [" + item.getCategoryCode().name() + "] " + item.getPromptContent());
        }
        return String.join("\n", lines);
    }

    private List<LlmPromptQuestionDraftRequest> parseQuestions(String rawText) {
        String text = stripMarkdownCodeFence(rawText);
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode array = root.isArray() ? root : root.get("questions");
            if (array == null || !array.isArray()) {
                throw new IllegalArgumentException("LLM response is not an array");
            }
            List<LlmPromptQuestionDraftRequest> result = new ArrayList<>();
            for (JsonNode node : array) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                JsonNode categoryNode = node.get("categoryCode");
                JsonNode contentNode = node.get("promptContent");
                if (categoryNode == null || contentNode == null || !categoryNode.isTextual() || !contentNode.isTextual()) {
                    continue;
                }
                LlmPromptQuestionDraftRequest item = new LlmPromptQuestionDraftRequest();
                item.setCategoryCode(PresalePromptCategoryCode.fromJson(categoryNode.asText()));
                item.setPromptContent(contentNode.asText());
                result.add(item);
            }
            return result;
        } catch (Exception ex) {
            log.error("LLM question response parse failed", ex);
            throw new BizException(400, "LLM 返回格式不正确，请重试");
        }
    }

    private LlmPromptQuestionGenerateVO buildResponse(Integer requestedTotal,
                                                      Map<PresalePromptCategoryCode, Integer> requestedCounts,
                                                      List<LlmPromptQuestionDraftRequest> generated,
                                                      Map<PresalePromptCategoryCode, Integer> missingCounts,
                                                      List<String> warnings) {
        LlmPromptQuestionGenerateVO vo = new LlmPromptQuestionGenerateVO();
        vo.setRequestedTotal(requestedTotal);
        vo.setGeneratedTotal(generated.size());
        vo.setMissingTotal(missingCounts.values().stream().mapToInt(Integer::intValue).sum());
        vo.setRequestedCategoryCounts(requestedCounts);
        vo.setGeneratedCategoryCounts(countByCategory(generated));
        vo.setMissingCategoryCounts(missingCounts);
        vo.setQuestions(generated.stream().map(this::toVO).toList());
        vo.setWarnings(warnings);
        return vo;
    }

    private LlmPromptQuestionDraftVO toVO(LlmPromptQuestionDraftRequest item) {
        LlmPromptQuestionDraftVO vo = new LlmPromptQuestionDraftVO();
        vo.setCategoryCode(item.getCategoryCode());
        vo.setCategoryLabel(item.getCategoryCode().getDisplayName());
        vo.setPromptContent(item.getPromptContent());
        vo.setHasCompetitorVar(item.getPromptContent().contains("{competitor}"));
        return vo;
    }

    private Map<PresalePromptCategoryCode, Integer> normalizeCounts(Map<PresalePromptCategoryCode, Integer> input) {
        Map<PresalePromptCategoryCode, Integer> result = new EnumMap<>(PresalePromptCategoryCode.class);
        if (input != null) {
            input.forEach((key, value) -> {
                if (key != null) {
                    result.put(key, value == null ? 0 : value);
                }
            });
        }
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            result.putIfAbsent(code, 0);
        }
        return result;
    }

    private Map<PresalePromptCategoryCode, Integer> countByCategory(List<LlmPromptQuestionDraftRequest> questions) {
        Map<PresalePromptCategoryCode, Integer> result = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            result.put(code, 0);
        }
        if (questions != null) {
            for (LlmPromptQuestionDraftRequest question : questions) {
                if (question != null && question.getCategoryCode() != null) {
                    result.merge(question.getCategoryCode(), 1, Integer::sum);
                }
            }
        }
        return result;
    }

    private Map<PresalePromptCategoryCode, Integer> missingCounts(Map<PresalePromptCategoryCode, Integer> targetCounts,
                                                                  Map<PresalePromptCategoryCode, Integer> actualCounts) {
        Map<PresalePromptCategoryCode, Integer> result = new EnumMap<>(PresalePromptCategoryCode.class);
        for (PresalePromptCategoryCode code : PresalePromptCategoryCode.values()) {
            result.put(code, Math.max(0, targetCounts.getOrDefault(code, 0) - actualCounts.getOrDefault(code, 0)));
        }
        return result;
    }

    private String stripMarkdownCodeFence(String text) {
        if (!StringUtils.hasText(text)) {
            return "[]";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeSnippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 300
                ? trimmed
                : trimmed.substring(0, 300);
    }

    private int extractHttpStatus(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("HTTP (\\d{3})").matcher(message);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            current = current.getCause();
        }
        return 500;
    }

    private static final class LlmQuestionProviderException extends Exception {
        private final String platformCode;
        private final int statusCode;
        private final String bodySnippet;

        private LlmQuestionProviderException(String platformCode, int statusCode, String bodySnippet) {
            super("HTTP " + statusCode + (StringUtils.hasText(bodySnippet) ? ": " + bodySnippet : ""));
            this.platformCode = platformCode;
            this.statusCode = statusCode;
            this.bodySnippet = bodySnippet;
        }

        private boolean isQuotaFailure() {
            return statusCode == 402 || statusCode == 429;
        }
    }
}
