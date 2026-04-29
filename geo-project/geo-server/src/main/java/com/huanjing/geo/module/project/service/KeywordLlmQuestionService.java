package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmHttpClient;
import com.huanjing.geo.module.project.dto.KeywordLlmQuestionGenerateVO;
import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeywordLlmQuestionService {

    public static final String TOKEN_PREFIX = "llm_gen_token:";
    private static final int MIN_TARGET_COUNT = 5;
    private static final int MAX_TARGET_COUNT = 50;
    private static final int DEFAULT_TARGET_COUNT = 30;
    private static final int DEFAULT_COUNT = 100;
    private static final int TIMEOUT_MS = 30_000;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String HEX = "0123456789abcdef";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LlmQuestionItemDTO>> LLM_ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private final CompanyMapper companyMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final PresaleLlmHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CurrentUserService currentUserService;

    public KeywordLlmQuestionGenerateVO generate(Long companyId, String seedText, String currentToken, Integer count, Integer currentLlmCount, Integer targetCount) {
        currentUserService.ensurePermission("keyword_group.read");
        requireCompany(companyId);
        String seed = parseSeed(seedText);
        int actualTarget = targetCount == null ? DEFAULT_TARGET_COUNT : targetCount;
        if (actualTarget < MIN_TARGET_COUNT || actualTarget > MAX_TARGET_COUNT) {
            throw coded("LLM_TARGET_COUNT_INVALID", "单次生成数量必须在 " + MIN_TARGET_COUNT + "-" + MAX_TARGET_COUNT + " 条之间");
        }

        String token = StringUtils.hasText(currentToken) ? currentToken.trim() : randomHex(32);
        List<LlmQuestionItemDTO> accumulated = StringUtils.hasText(currentToken) ? loadTokenItems(currentToken) : new ArrayList<>();
        int finalCount = count == null ? DEFAULT_COUNT : count;
        if (finalCount <= 0) {
            throw new BizException(400, "count must be > 0");
        }
        int retainedCount = currentLlmCount == null ? accumulated.size() : Math.max(0, currentLlmCount);
        if (retainedCount + actualTarget > finalCount) {
            throw coded("LLM_EXCEED_COUNT", "累积 LLM 问题将达 " + (retainedCount + actualTarget) + " 条,超过预览总数 " + finalCount + " 条");
        }

        List<String> questions = invokeWithRetry(seed, actualTarget);
        for (String question : questions) {
            accumulated.add(new LlmQuestionItemDTO(question, seed));
        }
        try {
            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, objectMapper.writeValueAsString(accumulated), TOKEN_TTL);
        } catch (Exception ex) {
            throw coded("LLM_GENERATE_FAILED", "AI 扩写失败,请稍后重试");
        }

        KeywordLlmQuestionGenerateVO vo = new KeywordLlmQuestionGenerateVO();
        vo.setGenerationToken(token);
        vo.setSeedText(seed);
        vo.setNewQuestions(questions);
        return vo;
    }

    public List<LlmQuestionItemDTO> loadTokenItems(String token) {
        if (!StringUtils.hasText(token)) {
            return List.of();
        }
        String stored = redisTemplate.opsForValue().get(TOKEN_PREFIX + token.trim());
        if (!StringUtils.hasText(stored)) {
            throw coded("LLM_QUESTION_TAMPERED", "LLM 生成已过期,请重新生成");
        }
        try {
            return objectMapper.readValue(stored, LLM_ITEM_LIST_TYPE);
        } catch (Exception ex) {
            throw coded("LLM_QUESTION_TAMPERED", "LLM 生成已过期,请重新生成");
        }
    }

    public void deleteToken(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(TOKEN_PREFIX + token.trim());
        }
    }

    public String parseSeed(String seedText) {
        String seed = seedText == null ? "" : seedText.trim();
        if (!StringUtils.hasText(seed)) {
            throw coded("LLM_SEED_INVALID_COUNT", "种子词不能为空");
        }
        if (seed.length() > 10) {
            throw coded("LLM_SEED_TOO_LONG", "种子词长度不能超过 10 字");
        }
        return seed;
    }

    private List<String> invokeWithRetry(String seed, int targetCount) {
        int minAccept = Math.max(3, targetCount * 2 / 3);
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                List<String> questions = invokeOnce(seed, targetCount);
                if (questions.size() >= targetCount) {
                    return questions.subList(0, targetCount);
                }
                if (questions.size() >= minAccept) {
                    return questions;
                }
                if (attempt == 1) {
                    throw coded("LLM_GENERATE_INSUFFICIENT", "AI 仅生成 " + questions.size() + " 条问题,请调整种子词或重试");
                }
            } catch (BizException ex) {
                if (ex.getMessage() != null && ex.getMessage().startsWith("LLM_GENERATE_INSUFFICIENT:")) {
                    throw ex;
                }
                lastError = ex;
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        throw coded("LLM_GENERATE_FAILED", "AI 扩写失败,请稍后重试");
    }

    private List<String> invokeOnce(String seed, int targetCount) throws Exception {
        AiPlatformConfig config = requirePlatformConfig();
        String modelId = StringUtils.hasText(config.getLowModelId()) ? config.getLowModelId().trim() : config.getModelId();
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing API key");
        }

        String body = buildRequestBody(modelId, renderPrompt(seed, targetCount));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);

        PresaleLlmHttpClient.HttpResponse response = httpClient.postJson(
                normalizeChatCompletionsUrl(config.getApiUrl()),
                headers,
                body,
                TIMEOUT_MS,
                TIMEOUT_MS
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return normalizeQuestions(extractText(response.body()));
    }

    private AiPlatformConfig requirePlatformConfig() {
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                PresalePlatformConfigQueries.presaleEnabledWrapper().last("LIMIT 1")
        );
        if (config == null || !StringUtils.hasText(config.getApiUrl())) {
            throw coded("LLM_GENERATE_FAILED", "AI 扩写失败,请稍后重试");
        }
        String modelId = StringUtils.hasText(config.getLowModelId()) ? config.getLowModelId() : config.getModelId();
        if (!StringUtils.hasText(modelId)) {
            throw coded("LLM_GENERATE_FAILED", "AI 扩写失败,请稍后重试");
        }
        return config;
    }

    private String buildRequestBody(String modelId, String prompt) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelId);
        payload.put("temperature", 0.4D);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "你是一个中文搜索与AI问答场景的问题扩写助手。"),
                Map.of("role", "user", "content", prompt)
        ));
        return objectMapper.writeValueAsString(payload);
    }

    private String renderPrompt(String seed, int targetCount) {
        return """
                请基于以下种子词生成 %d 条用户向搜索引擎/AI 提问的常见问题：%s

                要求:
                1. 每条独立,语义不重复
                2. 覆盖不同问法(哪里/哪家/怎么样/推荐/价格/对比/做法/特点)
                3. 长度 6-30 字
                4. 自然口语化
                输出格式:严格的 JSON 数组,不要任何其他内容。
                """.formatted(targetCount, seed);
    }

    private String extractText(String body) throws Exception {
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

    private List<String> normalizeQuestions(String rawText) throws Exception {
        String text = stripMarkdownCodeFence(rawText);
        List<String> parsed = objectMapper.readValue(text, STRING_LIST_TYPE);
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String question : parsed) {
            String normalized = question == null ? "" : question.trim();
            if (normalized.length() >= 6 && normalized.length() <= 30) {
                dedup.add(normalized);
            }
        }
        return new ArrayList<>(dedup);
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

    private void requireCompany(Long companyId) {
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getId, companyId).last("LIMIT 1"));
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX.charAt(RANDOM.nextInt(HEX.length())));
        }
        return sb.toString();
    }

    private BizException coded(String code, String message) {
        return new BizException(400, code + ": " + message);
    }
}
