package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionDraftRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionPlanRequest;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionDraftVO;
import com.huanjing.geo.module.presale.dto.response.LlmPromptQuestionGenerateVO;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmHttpClient;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresaleLlmPromptQuestionService {

    private static final int TIMEOUT_MS = 30_000;

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final PresaleLlmHttpClient httpClient;
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
        return errors;
    }

    private List<LlmPromptQuestionDraftRequest> invokeAndNormalize(LlmPromptQuestionGenerateRequest req,
                                                                   List<LlmPromptQuestionDraftRequest> existing,
                                                                   Map<PresalePromptCategoryCode, Integer> missingCounts) {
        String rawText;
        try {
            rawText = invokeOnce(renderUserPrompt(req, existing, missingCounts));
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

    private String invokeOnce(String userPrompt) throws Exception {
        AiPlatformConfig config = requirePlatformConfig();
        String modelId = StringUtils.hasText(config.getLowModelId()) ? config.getLowModelId().trim() : config.getModelId();
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing API key");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelId);
        payload.put("temperature", 0.4D);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", resolveSystemPrompt()),
                Map.of("role", "user", "content", userPrompt)
        ));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);

        PresaleLlmHttpClient.HttpResponse response = httpClient.postJson(
                normalizeChatCompletionsUrl(config.getApiUrl()),
                headers,
                objectMapper.writeValueAsString(payload),
                TIMEOUT_MS,
                TIMEOUT_MS
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return extractText(response.body());
    }

    private AiPlatformConfig requirePlatformConfig() {
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                PresalePlatformConfigQueries.presaleEnabledWrapper().last("LIMIT 1")
        );
        if (config == null || !StringUtils.hasText(config.getApiUrl())) {
            throw new BizException(400, "LLM 问题生成失败，请检查 AI 平台配置");
        }
        String modelId = StringUtils.hasText(config.getLowModelId()) ? config.getLowModelId() : config.getModelId();
        if (!StringUtils.hasText(modelId)) {
            throw new BizException(400, "LLM 问题生成失败，请检查 AI 平台模型配置");
        }
        return config;
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

    private String extractText(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            JsonNode message = first.get("message");
            if (message != null && message.get("content") != null && message.get("content").isTextual()) {
                return message.get("content").asText();
            }
            JsonNode text = first.get("text");
            if (text != null && text.isTextual()) {
                return text.asText();
            }
        }
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }
        return body;
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

    private String normalizeChatCompletionsUrl(String apiUrl) {
        String trimmed = apiUrl.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
