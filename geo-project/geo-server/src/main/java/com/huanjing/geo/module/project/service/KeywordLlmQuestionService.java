package com.huanjing.geo.module.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.LlmProperties;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.presale.generate.PresalePlatformConfigQueries;
import com.huanjing.geo.module.project.dto.KeywordLlmQuestionGenerateVO;
import com.huanjing.geo.module.project.dto.LlmQuestionItemDTO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
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
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String HEX = "0123456789abcdef";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LlmQuestionItemDTO>> LLM_ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final LlmCallFacade llmCallFacade;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;

    public KeywordLlmQuestionGenerateVO generate(Long companyId, Long projectId, String seedText, String currentToken, Integer count, Integer currentLlmCount, Integer targetCount) {
        currentUserService.ensurePermission("keyword_group.read");
        Project project = resolveProject(projectId, companyId);
        Long resolvedCompanyId = project == null ? companyId : project.getCompanyId();
        Company company = requireCompany(resolvedCompanyId);
        if (project != null) {
            internalScopeService.ensureProjectAccess(currentUserService.requireCurrentUser(), project, "project");
        } else {
            internalScopeService.ensureCompanyAccess(currentUserService.requireCurrentUser(), company, "company");
        }
        String seed = parseSeed(seedText);
        int actualTarget = targetCount == null ? DEFAULT_TARGET_COUNT : targetCount;
        if (actualTarget < MIN_TARGET_COUNT || actualTarget > MAX_TARGET_COUNT) {
            throw coded("LLM_TARGET_COUNT_INVALID", "单次生成数量必须在 " + MIN_TARGET_COUNT + "-" + MAX_TARGET_COUNT + " 条之间");
        }

        String token = StringUtils.hasText(currentToken) ? currentToken.trim() : randomHex(32);
        List<LlmQuestionItemDTO> accumulated = StringUtils.hasText(currentToken) ? loadTokenItems(currentToken) : new ArrayList<>();
        int projectLimit = project == null ? 0 : projectKeywordLimit(project);
        int finalCount = count == null ? (projectLimit > 0 ? projectLimit : DEFAULT_COUNT) : count;
        if (finalCount <= 0) {
            throw new BizException(400, "count must be > 0");
        }
        if (project != null && projectLimit <= 0) {
            throw coded("PROJECT_KEYWORD_QUOTA_EMPTY", "当前项目未配置问题额度");
        }
        if (project != null && finalCount > projectLimit) {
            throw coded("PROJECT_KEYWORD_QUOTA_EXCEEDED", "生成问题数量不能超过当前项目额度 " + projectLimit);
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

        String prompt = renderPrompt(seed, targetCount);
        LlmInvokeResult response = llmCallFacade.execute(LlmCallRequest.direct(prompt, new LlmModelConfig(
                config.getPlatformCode(),
                config.getPlatformName(),
                modelId,
                config.getModelName(),
                config.getApiUrl(),
                apiKey,
                "你是一个中文搜索与AI问答场景的问题扩写助手。",
                0.4D,
                llmProperties.getConnectTimeoutMs(),
                llmProperties.getRequestTimeoutMs(),
                0,
                Math.max(1, config.getRateLimitQps() == null ? 1 : config.getRateLimitQps()),
                null,
                false
        ))).invokeResult();
        return normalizeQuestions(response.responseText());
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

    private Company requireCompany(Long companyId) {
        if (companyId == null) {
            throw new BizException(400, "companyId or projectId is required");
        }
        Company company = companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getId, companyId).last("LIMIT 1"));
        if (company == null) {
            throw new BizException(404, "Company not found");
        }
        return company;
    }

    private Project resolveProject(Long projectId, Long companyId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        if (project.getCompanyId() == null) {
            throw new BizException(400, "Project company is missing");
        }
        if (companyId != null && !companyId.equals(project.getCompanyId())) {
            throw new BizException(400, "Project does not belong to company");
        }
        return project;
    }

    private int projectKeywordLimit(Project project) {
        int a = project.getPlanKeywordGroupLimitA() == null
                ? (project.getPlanKeywordGroupLimit() == null ? 0 : project.getPlanKeywordGroupLimit())
                : project.getPlanKeywordGroupLimitA();
        int b = project.getPlanKeywordGroupLimitB() == null ? 0 : project.getPlanKeywordGroupLimitB();
        int c = project.getPlanKeywordGroupLimitC() == null ? 0 : project.getPlanKeywordGroupLimitC();
        return a + b + c;
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
