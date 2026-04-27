package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.llm.JudgePromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.IndustryCoreAttributeConfigMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.entity.IndustryCoreAttributeConfig;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PresaleJudgeService {

    private static final String CATEGORY_COGNITIVE = "认知型";
    private static final String CATEGORY_COMPARISON = "对比型";
    private static final String CATEGORY_COGNITIVE_DB = "COGNITIVE";
    private static final String CATEGORY_COMPARISON_DB = "COMPARISON";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int JUDGE_ERROR_MAX_LEN = 500;
    private static final String COMMON_ATTRIBUTE_INDUSTRY = "_ALL_";
    private static final List<String> DEFAULT_COGNITIVE_ATTRIBUTES = List.of(
            "品牌历史", "产品", "服务", "价格", "口碑", "创新", "规模", "影响力"
    );

    private final PresaleAiPromptResultMapper promptResultMapper;
    private final PresaleAiPromptJudgeResultMapper judgeResultMapper;
    private final IndustryCoreAttributeConfigMapper attributeConfigMapper;
    private final PresaleReportVersionMapper reportVersionMapper;
    private final PresaleReportMapper reportMapper;
    private final PresaleLlmInvoker llmInvoker;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final ObjectMapper objectMapper;
    private final Executor judgeExecutor;

    @Value("${presale.judge.retry.max-attempts:2}")
    private int judgeMaxAttempts;

    @Value("${presale.judge.temperature:0}")
    private double judgeTemperature;

    public PresaleJudgeService(PresaleAiPromptResultMapper promptResultMapper,
                               PresaleAiPromptJudgeResultMapper judgeResultMapper,
                               IndustryCoreAttributeConfigMapper attributeConfigMapper,
                               PresaleReportVersionMapper reportVersionMapper,
                               PresaleReportMapper reportMapper,
                               PresaleLlmInvoker llmInvoker,
                               AiPlatformConfigMapper aiPlatformConfigMapper,
                               ObjectMapper objectMapper,
                               @Qualifier("presaleJudgeExecutor") Executor judgeExecutor) {
        this.promptResultMapper = promptResultMapper;
        this.judgeResultMapper = judgeResultMapper;
        this.attributeConfigMapper = attributeConfigMapper;
        this.reportVersionMapper = reportVersionMapper;
        this.reportMapper = reportMapper;
        this.llmInvoker = llmInvoker;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.objectMapper = objectMapper;
        this.judgeExecutor = judgeExecutor;
    }

    public void judgeCognitiveAfterBatch1(Long versionId, String brandName, Long operatorUserId, boolean isManager) {
        runJudge(versionId, 1, CATEGORY_COGNITIVE, brandName, operatorUserId, isManager);
    }

    public void judgeComparisonAfterBatch2(Long versionId, String brandName, Long operatorUserId, boolean isManager) {
        runJudge(versionId, 2, CATEGORY_COMPARISON, brandName, operatorUserId, isManager);
    }

    private void runJudge(Long versionId,
                          int batchNo,
                          String category,
                          String brandName,
                          Long operatorUserId,
                          boolean isManager) {
        List<PresaleJudgeCandidateRow> candidates = promptResultMapper.selectJudgeCandidatesByVersionAndCategory(
                versionId, batchNo, category
        );
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        Map<String, List<PresaleJudgeCandidateRow>> byPlatform = new LinkedHashMap<>();
        for (PresaleJudgeCandidateRow candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getPlatformCode())) {
                continue;
            }
            byPlatform.computeIfAbsent(candidate.getPlatformCode(), k -> new ArrayList<>()).add(candidate);
        }
        if (byPlatform.isEmpty()) {
            return;
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, List<PresaleJudgeCandidateRow>> entry : byPlatform.entrySet()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (PresaleJudgeCandidateRow candidate : entry.getValue()) {
                    JudgeOutcome outcome = processOneCandidate(candidate, category, brandName, operatorUserId, isManager);
                    if (outcome == JudgeOutcome.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (outcome == JudgeOutcome.FAILED) {
                        failedCount.incrementAndGet();
                    }
                }
            }, judgeExecutor);
            futures.add(future);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.join();
        } catch (CompletionException ex) {
            log.error("judge execution completion exception, versionId={}, batchNo={}, category={}",
                    versionId, batchNo, category, ex);
        }

        log.info("judge done, versionId={}, batchNo={}, category={}, candidates={}, success={}, failed={}",
                versionId, batchNo, category, candidates.size(), successCount.get(), failedCount.get());
    }

    private JudgeOutcome processOneCandidate(PresaleJudgeCandidateRow candidate,
                                             String category,
                                             String brandName,
                                             Long operatorUserId,
                                             boolean isManager) {
        if (candidate == null || candidate.getPromptResultId() == null) {
            return JudgeOutcome.SKIPPED;
        }
        if (hasSuccessfulJudge(candidate, category)) {
            return JudgeOutcome.SKIPPED;
        }

        String modelId = resolveModelId(candidate.getPlatformCode());
        if (!StringUtils.hasText(candidate.getQueryAnswer())) {
            upsertJudgeFailure(candidate, category, 1, "QUERY_ANSWER_EMPTY", null, modelId);
            return JudgeOutcome.FAILED;
        }

        int maxAttempts = Math.max(1, judgeMaxAttempts);
        List<String> cognitiveAttributes = CATEGORY_COGNITIVE.equals(category)
                ? resolveCognitiveAttributes(candidate.getVersionId())
                : DEFAULT_COGNITIVE_ATTRIBUTES;
        String judgePrompt = buildJudgePrompt(category, brandName, candidate.getCompetitorName(), candidate.getQueryAnswer(), cognitiveAttributes);
        JudgeAttemptError lastError = null;
        int attemptsUsed = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptsUsed = attempt;
            try {
                List<PresaleAiPromptJudgeResult> successRows = invokeAndBuildSuccess(
                        candidate, category, brandName, operatorUserId, isManager, judgePrompt, modelId, attempt, cognitiveAttributes
                );
                cleanupLegacyComparisonGroupJudge(candidate, category);
                for (PresaleAiPromptJudgeResult successRow : successRows) {
                    upsertJudgeSuccess(successRow);
                }
                return JudgeOutcome.SUCCESS;
            } catch (JudgeAttemptError ex) {
                lastError = ex;
                if (!ex.retryableInOuterLoop()) {
                    break;
                }
            }
        }

        String errorMessage = lastError == null ? "JUDGE_UNKNOWN_ERROR" : lastError.getMessage();
        String rawResponse = lastError == null ? null : lastError.rawResponse();
        upsertJudgeFailure(candidate, category, attemptsUsed, errorMessage, rawResponse, modelId);
        return JudgeOutcome.FAILED;
    }

    private List<PresaleAiPromptJudgeResult> invokeAndBuildSuccess(PresaleJudgeCandidateRow candidate,
                                                                   String category,
                                                                   String brandName,
                                                                   Long operatorUserId,
                                                                   boolean isManager,
                                                                   String judgePrompt,
                                                                   String modelId,
                                                                   int attempt,
                                                                   List<String> cognitiveAttributes) throws JudgeAttemptError {
        String competitorName = normalizeCompetitor(candidate.getCompetitorName());
        PlatformCallContext ctx = new PlatformCallContext(
                candidate.getVersionId(),
                candidate.getBatchNo(),
                candidate.getPlatformCode(),
                candidate.getPromptTemplateId(),
                competitorName,
                brandName,
                operatorUserId,
                isManager
        );
        LlmCallResult result;
        try {
            result = llmInvoker.judge(ctx, judgePrompt, judgeTemperature);
        } catch (LlmInvokeException ex) {
            throw new JudgeAttemptError(JudgeErrorCode.LLM_CALL_FAILED,
                    "JUDGE_LLM_CALL_FAILED: " + safeMessage(ex), null, ex);
        }
        JsonNode payload = parseJudgePayload(result.rawResponse());
        if (CATEGORY_COGNITIVE.equals(category)) {
            PresaleAiPromptJudgeResult row = initBaseJudgeRow(candidate, category);
            applySuccessMeta(row, attempt, modelId, result.rawResponse(), payload);
            applyCognitivePayload(row, payload, cognitiveAttributes);
            return List.of(row);
        } else if (CATEGORY_COMPARISON.equals(category)) {
            return buildComparisonRows(candidate, attempt, modelId, result.rawResponse(), payload);
        } else {
            throw new JudgeAttemptError(JudgeErrorCode.UNSUPPORTED_CATEGORY,
                    "UNSUPPORTED_CATEGORY: " + category, result.rawResponse(), null);
        }
    }

    private void applySuccessMeta(PresaleAiPromptJudgeResult row,
                                  int attempt,
                                  String modelId,
                                  String rawResponse,
                                  JsonNode payload) {
        row.setJudgeStatus(STATUS_SUCCESS);
        row.setJudgeAttemptCount(attempt);
        row.setJudgeModelId(modelId);
        row.setJudgeTemperature(BigDecimal.valueOf(judgeTemperature).setScale(2, RoundingMode.HALF_UP));
        row.setJudgeError(null);
        row.setRawJudgeResponse(rawResponse);
        row.setJudgePayloadJson(toJsonOrNull(payload));
    }

    private JsonNode parseJudgePayload(String rawResponse) throws JudgeAttemptError {
        if (!StringUtils.hasText(rawResponse)) {
            throw new JudgeAttemptError(JudgeErrorCode.EMPTY_RESPONSE, "JUDGE_EMPTY_RESPONSE", rawResponse, null);
        }
        try {
            JsonNode node = objectMapper.readTree(rawResponse);
            if (node == null || !node.isObject()) {
                throw new JudgeAttemptError(JudgeErrorCode.RESPONSE_NOT_OBJECT,
                        "JUDGE_RESPONSE_NOT_OBJECT", rawResponse, null);
            }
            return node;
        } catch (JudgeAttemptError ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JudgeAttemptError(JudgeErrorCode.JSON_PARSE_FAILED,
                    "JUDGE_JSON_PARSE_FAILED: " + safeMessage(ex), rawResponse, ex);
        }
    }

    private void applyCognitivePayload(PresaleAiPromptJudgeResult row,
                                       JsonNode payload,
                                       List<String> cognitiveAttributes) throws JudgeAttemptError {
        BigDecimal sentimentScore = parseScore(payload.get("sentiment_score"), -1D, 1D);
        if (sentimentScore == null) {
            throw new JudgeAttemptError(JudgeErrorCode.INVALID_SENTIMENT_SCORE,
                    "JUDGE_INVALID_SENTIMENT_SCORE", null, null);
        }
        row.setSentimentScore(sentimentScore);
        String rawSentiment = payload.path("sentiment").asText(null);
        String llmSentiment = normalizeEnumText(rawSentiment,
                Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "UNKNOWN"));
        if (StringUtils.hasText(rawSentiment) && llmSentiment == null) {
            log.warn("judge cognitive sentiment illegal enum, promptResultId={}, rawSentiment={}",
                    row.getPromptResultId(), rawSentiment);
        }
        String recomputedSentiment = recomputeSentimentByScore(sentimentScore);
        if (llmSentiment != null && !llmSentiment.equals(recomputedSentiment)) {
            log.warn("judge cognitive sentiment mismatch, promptResultId={}, llm={}, score={}, recomputed={}",
                    row.getPromptResultId(), llmSentiment, sentimentScore, recomputedSentiment);
        }
        row.setSentiment(recomputedSentiment);

        List<String> attrs = filterCognitiveAttributes(readStringArray(payload.get("attributes_hit")), cognitiveAttributes);
        row.setAttributesHit(toJsonOrNull(attrs));
        row.setAttributeHitRate(calculateAttributeHitRate(attrs, cognitiveAttributes));

        List<String> factualErrors = readStringArray(payload.get("factual_errors"));
        row.setFactualErrors(toJsonOrNull(factualErrors));

        String tone = normalizeEnumText(payload.path("tone").asText(null),
                Set.of("OBJECTIVE", "PROMOTIONAL", "MIXED", "UNKNOWN"));
        row.setTone(tone);
    }

    private List<PresaleAiPromptJudgeResult> buildComparisonRows(PresaleJudgeCandidateRow candidate,
                                                                 int attempt,
                                                                 String modelId,
                                                                 String rawResponse,
                                                                 JsonNode payload) throws JudgeAttemptError {
        List<String> expectedCompetitors = CompetitorGroupKeyUtils.split(candidate.getCompetitorName());
        if (expectedCompetitors.isEmpty()) {
            expectedCompetitors = List.of(normalizeCompetitor(candidate.getCompetitorName()));
        }
        Set<String> expected = new LinkedHashSet<>(expectedCompetitors);
        JsonNode verdicts = payload.get("verdicts");
        if (verdicts == null || !verdicts.isArray()) {
            throw new JudgeAttemptError(JudgeErrorCode.INVALID_VERDICTS,
                    "JUDGE_INVALID_VERDICTS", rawResponse, null);
        }

        Map<String, JsonNode> verdictByCompetitor = new LinkedHashMap<>();
        for (JsonNode verdict : verdicts) {
            String competitor = normalizeCompetitor(verdict.path("competitor").asText(null));
            if (!StringUtils.hasText(competitor) || !expected.contains(competitor)) {
                throw new JudgeAttemptError(JudgeErrorCode.INVALID_VERDICTS,
                        "JUDGE_INVALID_VERDICT_COMPETITOR: " + competitor, rawResponse, null);
            }
            if (verdictByCompetitor.putIfAbsent(competitor, verdict) != null) {
                throw new JudgeAttemptError(JudgeErrorCode.INVALID_VERDICTS,
                        "JUDGE_DUPLICATE_VERDICT_COMPETITOR: " + competitor, rawResponse, null);
            }
        }
        if (verdictByCompetitor.size() != expected.size()) {
            throw new JudgeAttemptError(JudgeErrorCode.INVALID_VERDICTS,
                    "JUDGE_VERDICTS_NOT_COVER_ALL_COMPETITORS", rawResponse, null);
        }

        List<PresaleAiPromptJudgeResult> rows = new ArrayList<>();
        for (String competitor : expectedCompetitors) {
            PresaleAiPromptJudgeResult row = initBaseJudgeRow(candidate, CATEGORY_COMPARISON);
            row.setCompetitorName(competitor);
            JsonNode verdict = verdictByCompetitor.get(competitor);
            applySuccessMeta(row, attempt, modelId, rawResponse, verdict);
            applyComparisonPayload(row, verdict);
            rows.add(row);
        }
        return rows;
    }

    private void applyComparisonPayload(PresaleAiPromptJudgeResult row, JsonNode payload) throws JudgeAttemptError {
        String preferredBrand = normalizeEnumText(payload.path("preferred_brand").asText(null),
                Set.of("target", "competitor", "tie", "unclear"));
        if (!StringUtils.hasText(preferredBrand)) {
            throw new JudgeAttemptError(JudgeErrorCode.INVALID_PREFERRED_BRAND,
                    "JUDGE_INVALID_PREFERRED_BRAND", null, null);
        }
        row.setPreferredBrand(preferredBrand.toLowerCase(Locale.ROOT));

        String targetSentiment = normalizeEnumText(payload.path("target_sentiment").asText(null),
                Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "UNKNOWN"));
        row.setTargetSentiment(targetSentiment);

        List<String> targetAdvantages = readStringArray(payload.get("target_advantages"));
        List<String> targetDisadvantages = readStringArray(payload.get("target_disadvantages"));
        List<String> competitorAdvantages = readStringArray(payload.get("competitor_advantages"));
        row.setTargetAdvantages(toJsonOrNull(targetAdvantages));
        row.setTargetDisadvantages(toJsonOrNull(targetDisadvantages));
        row.setCompetitorAdvantages(toJsonOrNull(competitorAdvantages));

        String reasoningQuality = normalizeEnumText(payload.path("reasoning_quality").asText(null),
                Set.of("high", "medium", "low", "unknown"));
        row.setReasoningQuality(reasoningQuality == null ? null : reasoningQuality.toLowerCase(Locale.ROOT));
    }

    private List<String> filterCognitiveAttributes(List<String> source, List<String> cognitiveAttributes) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> attributeSet = new LinkedHashSet<>(safeCognitiveAttributes(cognitiveAttributes));
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String normalized = item.trim();
            if (attributeSet.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        return new ArrayList<>(filtered);
    }

    private BigDecimal calculateAttributeHitRate(List<String> attrs, List<String> cognitiveAttributes) {
        int numerator = attrs == null ? 0 : attrs.size();
        int denominator = safeCognitiveAttributes(cognitiveAttributes).size();
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal value = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = item.asText(null);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            values.add(text.trim());
        }
        return values;
    }

    private BigDecimal parseScore(JsonNode node, double min, double max) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = node.asDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeEnumText(String raw, Set<String> allowed) {
        if (!StringUtils.hasText(raw) || allowed == null || allowed.isEmpty()) {
            return null;
        }
        String value = raw.trim();
        if (allowed.stream().allMatch(v -> v.equals(v.toLowerCase(Locale.ROOT)))) {
            String lower = value.toLowerCase(Locale.ROOT);
            return allowed.contains(lower) ? lower : null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }

    private PresaleAiPromptJudgeResult initBaseJudgeRow(PresaleJudgeCandidateRow candidate, String sourceCategory) {
        PresaleAiPromptJudgeResult row = new PresaleAiPromptJudgeResult();
        row.setPromptResultId(candidate.getPromptResultId());
        row.setVersionId(candidate.getVersionId());
        row.setBatchNo(candidate.getBatchNo());
        row.setPlatformCode(candidate.getPlatformCode());
        row.setPromptTemplateId(candidate.getPromptTemplateId());
        row.setCategory(mapCategoryToDb(sourceCategory));
        row.setCompetitorName(normalizeCompetitor(candidate.getCompetitorName()));
        return row;
    }

    private void upsertJudgeSuccess(PresaleAiPromptJudgeResult row) {
        judgeResultMapper.upsertByPromptResultId(row);
    }

    private void cleanupLegacyComparisonGroupJudge(PresaleJudgeCandidateRow candidate, String category) {
        if (!CATEGORY_COMPARISON.equals(category) || candidate == null || candidate.getPromptResultId() == null) {
            return;
        }
        String groupName = normalizeCompetitor(candidate.getCompetitorName());
        if (!StringUtils.hasText(groupName) || !groupName.contains(CompetitorGroupKeyUtils.SEPARATOR)) {
            return;
        }
        judgeResultMapper.delete(new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                .eq(PresaleAiPromptJudgeResult::getPromptResultId, candidate.getPromptResultId())
                .eq(PresaleAiPromptJudgeResult::getCompetitorName, groupName));
    }

    private void upsertJudgeFailure(PresaleJudgeCandidateRow candidate,
                                    String category,
                                    int attemptCount,
                                    String errorMessage,
                                    String rawResponse,
                                    String modelId) {
        PresaleAiPromptJudgeResult row = initBaseJudgeRow(candidate, category);
        row.setJudgeStatus(STATUS_FAILED);
        row.setJudgeAttemptCount(attemptCount);
        row.setJudgeModelId(modelId);
        row.setJudgeTemperature(BigDecimal.valueOf(judgeTemperature).setScale(2, RoundingMode.HALF_UP));
        row.setJudgeError(truncate(errorMessage, JUDGE_ERROR_MAX_LEN));
        row.setRawJudgeResponse(rawResponse);
        row.setJudgePayloadJson(null);
        clearAllJudgeFields(row);
        judgeResultMapper.upsertByPromptResultId(row);
    }

    private void clearAllJudgeFields(PresaleAiPromptJudgeResult row) {
        row.setSentiment(null);
        row.setSentimentScore(null);
        row.setAttributeHitRate(null);
        row.setTone(null);
        row.setPreferredBrand(null);
        row.setTargetSentiment(null);
        row.setReasoningQuality(null);
        row.setAttributesHit(null);
        row.setFactualErrors(null);
        row.setTargetAdvantages(null);
        row.setTargetDisadvantages(null);
        row.setCompetitorAdvantages(null);
    }

    private boolean hasSuccessfulJudge(PresaleJudgeCandidateRow candidate, String category) {
        if (candidate == null || candidate.getPromptResultId() == null) {
            return false;
        }
        if (CATEGORY_COMPARISON.equals(category)) {
            int expectedCount = Math.max(1, CompetitorGroupKeyUtils.split(candidate.getCompetitorName()).size());
            Long count = judgeResultMapper.selectCount(new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                    .eq(PresaleAiPromptJudgeResult::getPromptResultId, candidate.getPromptResultId())
                    .eq(PresaleAiPromptJudgeResult::getCategory, CATEGORY_COMPARISON_DB)
                    .eq(PresaleAiPromptJudgeResult::getJudgeStatus, STATUS_SUCCESS));
            return count != null && count >= expectedCount;
        }
        Long count = judgeResultMapper.selectCount(new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                .eq(PresaleAiPromptJudgeResult::getPromptResultId, candidate.getPromptResultId())
                .eq(PresaleAiPromptJudgeResult::getCategory, CATEGORY_COGNITIVE_DB)
                .eq(PresaleAiPromptJudgeResult::getJudgeStatus, STATUS_SUCCESS));
        return count != null && count > 0;
    }

    private String resolveModelId(String platformCode) {
        if (!StringUtils.hasText(platformCode)) {
            return null;
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                PresalePlatformConfigQueries.presaleEnabledWrapper()
                        .eq(AiPlatformConfig::getPlatformCode, platformCode.trim())
                        .last("LIMIT 1")
        );
        if (config == null) {
            return null;
        }
        if (StringUtils.hasText(config.getLowModelId())) {
            return config.getLowModelId().trim();
        }
        return StringUtils.hasText(config.getModelId()) ? config.getModelId().trim() : null;
    }

    private List<String> resolveCognitiveAttributes(Long versionId) {
        String industry = resolveIndustry(versionId);
        List<String> industryAttributes = loadEnabledAttributes(industry);
        if (!industryAttributes.isEmpty()) {
            return industryAttributes;
        }
        List<String> commonAttributes = loadEnabledAttributes(COMMON_ATTRIBUTE_INDUSTRY);
        return commonAttributes.isEmpty() ? DEFAULT_COGNITIVE_ATTRIBUTES : commonAttributes;
    }

    private String resolveIndustry(Long versionId) {
        if (versionId == null) {
            return null;
        }
        PresaleReportVersion version = reportVersionMapper.selectById(versionId);
        if (version == null || version.getReportId() == null) {
            return null;
        }
        PresaleReport report = reportMapper.selectById(version.getReportId());
        return report == null ? null : report.getIndustry();
    }

    private List<String> loadEnabledAttributes(String industry) {
        if (!StringUtils.hasText(industry)) {
            return Collections.emptyList();
        }
        IndustryCoreAttributeConfig config = attributeConfigMapper.selectOne(
                new LambdaQueryWrapper<IndustryCoreAttributeConfig>()
                        .eq(IndustryCoreAttributeConfig::getIndustry, industry)
                        .eq(IndustryCoreAttributeConfig::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (config == null || !StringUtils.hasText(config.getAttributesJson())) {
            return Collections.emptyList();
        }
        return readJsonStringArray(config.getAttributesJson());
    }

    private List<String> readJsonStringArray(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return readStringArray(node);
        } catch (Exception ex) {
            log.warn("invalid industry core attribute config json, json={}", json, ex);
            return Collections.emptyList();
        }
    }

    private List<String> safeCognitiveAttributes(List<String> cognitiveAttributes) {
        return cognitiveAttributes == null || cognitiveAttributes.isEmpty()
                ? DEFAULT_COGNITIVE_ATTRIBUTES
                : cognitiveAttributes;
    }

    private String buildJudgePrompt(String category,
                                    String brandName,
                                    String competitorName,
                                    String answer,
                                    List<String> cognitiveAttributes) {
        String safeBrand = safeText(brandName);
        String safeAnswer = safeText(answer);
        if (CATEGORY_COGNITIVE.equals(category)) {
            return JudgePromptTemplates.COGNITIVE_TEMPLATE
                    .replace("{brand}", safeBrand)
                    .replace("{attributes}", String.join("、", safeCognitiveAttributes(cognitiveAttributes)))
                    .replace("{answer}", safeAnswer);
        }
        if (CATEGORY_COMPARISON.equals(category)) {
            return JudgePromptTemplates.COMPARISON_TEMPLATE
                    .replace("{brand}", safeBrand)
                    .replace("{competitor}", safeText(normalizeCompetitor(competitorName)))
                    .replace("{answer}", safeAnswer);
        }
        throw new IllegalArgumentException("unsupported judge category: " + category);
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeCompetitor(String competitorName) {
        return StringUtils.hasText(competitorName) ? competitorName.trim() : "";
    }

    private String mapCategoryToDb(String sourceCategory) {
        if (CATEGORY_COGNITIVE.equals(sourceCategory)) {
            return CATEGORY_COGNITIVE_DB;
        }
        if (CATEGORY_COMPARISON.equals(sourceCategory)) {
            return CATEGORY_COMPARISON_DB;
        }
        throw new IllegalArgumentException("unsupported source category: " + sourceCategory);
    }

    private String recomputeSentimentByScore(BigDecimal sentimentScore) {
        double score = sentimentScore.doubleValue();
        if (score > 0.33D) {
            return "POSITIVE";
        }
        if (score < -0.33D) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private String truncate(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= maxLen) {
            return raw;
        }
        return raw.substring(0, maxLen);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private String safeText(String input) {
        return input == null ? "" : input;
    }

    private enum JudgeOutcome {
        SUCCESS,
        FAILED,
        SKIPPED
    }

    private enum JudgeErrorCode {
        LLM_CALL_FAILED(false),
        EMPTY_RESPONSE(true),
        RESPONSE_NOT_OBJECT(true),
        JSON_PARSE_FAILED(true),
        UNSUPPORTED_CATEGORY(false),
        INVALID_PREFERRED_BRAND(false),
        INVALID_VERDICTS(false),
        INVALID_SENTIMENT_SCORE(false);

        private final boolean retryableInOuterLoop;

        JudgeErrorCode(boolean retryableInOuterLoop) {
            this.retryableInOuterLoop = retryableInOuterLoop;
        }

        private boolean retryableInOuterLoop() {
            return retryableInOuterLoop;
        }
    }

    private static final class JudgeAttemptError extends Exception {
        private final JudgeErrorCode code;
        private final String rawResponse;

        private JudgeAttemptError(JudgeErrorCode code, String message, String rawResponse, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.rawResponse = rawResponse;
        }

        private String rawResponse() {
            return rawResponse;
        }

        private boolean retryableInOuterLoop() {
            return code != null && code.retryableInOuterLoop();
        }
    }
}
