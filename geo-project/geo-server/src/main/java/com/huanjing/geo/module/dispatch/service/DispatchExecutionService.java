package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailure;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmPlatformCodeFilters;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.common.util.EntityMatchTextNormalizer;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.entity.PackageContentConfig;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.PackageContentConfigMapper;
import com.huanjing.geo.module.content.service.ArticleForbiddenPhrasePolicy;
import com.huanjing.geo.module.content.service.ArticleGenerationPersistenceService;
import com.huanjing.geo.module.content.service.ContentArticleService;
import com.huanjing.geo.module.content.service.GeoPromptBuilder;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.websearch.WebSearchPollCommand;
import com.huanjing.geo.module.dispatch.websearch.QuestionPollPromptTemplate;
import com.huanjing.geo.module.dispatch.websearch.WebSearchPollExecutionOutcome;
import com.huanjing.geo.module.dispatch.websearch.WebSearchPollExecutionService;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.TriggerType;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchExecutionService {

    private static final Pattern URL_QUERY_PATTERN = Pattern.compile("(https?://[^\\s?#]+)(\\?[^\\s#]*)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(api[_-]?key|token)\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern PHONE_TEXT_PATTERN = Pattern.compile("(\\+?\\d[\\d\\-\\s()]{5,}\\d)");
    private static final int DB_TRANSIENT_RETRY_DELAY_MS = 200;
    private static final int QUESTION_POLL_MIN_REQUEST_TIMEOUT_MS = 5_000;
    private static final int QUESTION_POLL_TASK_TIMEOUT_SAFETY_MS = 60_000;
    private static final String QUESTION_POLL_MODEL_TIER_LOW = "low";
    private static final Set<String> QUESTION_TIERS = Set.of("A", "B", "C");

    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final LlmCallFacade llmCallFacade;
    private final ProjectMapper projectMapper;
    private final PackageContentConfigMapper packageContentConfigMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ContentArticleService contentArticleService;
    private final GeoPromptBuilder geoPromptBuilder;
    private final ArticleGenerationPersistenceService articleGenerationPersistenceService;
    private final ArticleGenerationWindowLockService articleGenerationWindowLockService;
    private final PollBatchMapper pollBatchMapper;
    private final PollBatchShardItemMapper pollBatchShardItemMapper;
    private final PollResultMapper pollResultMapper;
    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final BrandStatementService brandStatementService;
    private final SysDictItemMapper sysDictItemMapper;
    private final DispatchProperties dispatchProperties;
    private final DispatchQuestionPollPlanningService questionPollPlanningService;
    private final DispatchPollShardPersistenceService pollShardPersistenceService;
    private final DispatchPollAggregationService pollAggregationService;
    private final WebSearchPollExecutionService webSearchPollExecutionService;
    private final LlmCapacityFailureClassifier capacityFailureClassifier;

    @Value("${geo.llm.routing.article-excluded-platform-codes:hunyuan,yuanbao}")
    private String articleExcludedPlatformCodes = "hunyuan,yuanbao";

    public void execute(DispatchTask task) {
        DispatchTaskType type = DispatchTaskType.fromValue(task.getTaskType());
        if (type == DispatchTaskType.BIWEEKLY_REPORT
                || type == DispatchTaskType.MONTHLY_REPORT
                || type == DispatchTaskType.QUARTERLY_REPORT) {
            throw new BizException(410, "report generation disabled by product policy");
        }
        List<AiPlatformConfig> platformConfigs = resolvePlatformCandidates(task.getProjectId(), type);
        if (platformConfigs.isEmpty()) {
            if (type == DispatchTaskType.CONTENT_GENERATION) {
                throw new BizException(400, "No article-enabled platform configured");
            }
            throw new BizException(400, "No enabled platform configured for task type " + task.getTaskType());
        }
        if (type == DispatchTaskType.QUESTION_POLL) {
            executeQuestionPoll(task, platformConfigs);
            return;
        }
        if (type == DispatchTaskType.CONTENT_GENERATION) {
            executeContentGeneration(task, platformConfigs);
            return;
        }
        if (type == DispatchTaskType.BRAND_STATEMENT_GENERATION) {
            executeBrandStatementGeneration(task, platformConfigs);
            return;
        }

        Exception lastError = null;
        for (AiPlatformConfig config : platformConfigs) {
            InvocationResult result = invokeWithFallback(config, task, null);
            if (result.success) {
                return;
            }
            lastError = result.error;
            log.warn("Invocation failed for platform={}, reason={}", config.getPlatformCode(), result.errorMessage);
        }
        if (lastError != null) {
            throw new BizException(500, "All provider paths failed: " + lastError.getMessage());
        }
        throw new BizException(500, "All provider paths failed");
    }

    private void executeQuestionPoll(DispatchTask task, List<AiPlatformConfig> platformConfigs) {
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        Long shardId = resolveShardId(task);
        if (shardId != null) {
            executeQuestionPollShard(task, shardId);
            return;
        }
        String questionTier = resolveQuestionTier(task);
        questionPollPlanningService.planFromLegacyTask(task, project, resolveBatchDate(task), resolveBatchNo(task), questionTier);
    }

    private void executeQuestionPollShard(DispatchTask task, Long shardId) {
        PollBatchShard shard = pollShardPersistenceService.markShardRunning(shardId, task.getId());
        if (shard == null) {
            throw new BizException(404, "Question poll shard not found: " + shardId);
        }
        if (DispatchPollShardPersistenceService.SHARD_STATUS_COMPLETED.equals(shard.getStatus())) {
            pollAggregationService.tryAggregateBatch(shard.getBatchId());
            return;
        }
        PollBatch batch = pollBatchMapper.selectById(shard.getBatchId());
        if (batch == null) {
            throw new BizException(404, "Question poll batch not found: " + shard.getBatchId());
        }
        Project project = projectMapper.selectById(shard.getProjectId());
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        AiPlatformConfig platform = aiPlatformConfigMapper.selectById(shard.getPlatformId());
        if (platform == null || !Boolean.TRUE.equals(platform.getEnabled())) {
            throw new BizException(400, "Question poll platform unavailable: " + shard.getPlatformCode());
        }
        platform = resolveQuestionPollModelConfig(platform);
        List<PollBatchShardItem> items = pollBatchShardItemMapper.selectByShardId(shardId);
        if (items.isEmpty()) {
            pollShardPersistenceService.markShardCompleted(shardId);
            pollAggregationService.tryAggregateBatch(shard.getBatchId());
            return;
        }

        Set<String> projectNames = resolveProjectNameSet(project);
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        Set<String> judgeBrandNames = resolveJudgeBrandNameSet(project, brand);
        Set<String> siteDomains = resolveSiteDomains(project);
        Set<String> normalizedPhones = resolvePhones(project);
        Set<String> contactTerms = resolveContactTerms(project);
        try {
            for (int i = 0; i < items.size(); i++) {
                PollBatchShardItem item = items.get(i);
                if (isTerminalPollItem(item)) {
                    continue;
                }
                PollResult stagedResult = pollShardPersistenceService.readStagedPollResult(item);
                if (stagedResult != null) {
                    pollShardPersistenceService.upsertPollResultAndMarkItem(stagedResult, item);
                    continue;
                }
                int remainingItems = countRemainingPollItems(items, i);
                PollKeywordCandidate keyword = new PollKeywordCandidate(item.getKeywordResultId(), item.getKeywordTextSnapshot());
                InvocationResult invokeResult;
                Integer requestTimeoutMs = resolveMonitoringRequestTimeoutMs(platform, task, remainingItems);
                if (requestTimeoutMs == null) {
                    invokeResult = InvocationResult.failure(
                            "QUESTION_POLL_BUDGET_EXHAUSTED",
                            "Question poll shard budget exhausted before model invocation",
                            0,
                            null
                    );
                } else if (isWebSearchPlatform(platform)) {
                    PollResult pending = buildPollResultIdentity(batch, task, project, platform, keyword);
                    pending.setStatus("running");
                    pending.setRecordType("pending");
                    pending.setRequestCount(0);
                    pending.setDetailJson("{}");
                    PollResult persisted = pollShardPersistenceService.ensurePollResult(pending);
                    WebSearchPollExecutionOutcome outcome = webSearchPollExecutionService.execute(
                            new WebSearchPollCommand(
                                    persisted.getId(), item.getId(), task.getId(), project.getId(),
                                    keyword.keywordResultId(), keyword.keywordText(),
                                    QuestionPollPromptTemplate.SYSTEM_PROMPT,
                                    platform,
                                    resolvePollTriggerType(batch),
                                    dispatchProperties.getModelConnectTimeoutMs(),
                                    requestTimeoutMs,
                                    judgeBrandNames
                            )
                    );
                    task.setPlatformCode(platform.getPlatformCode());
                    task.setCurrentChannel("web_search");
                    invokeResult = outcome.success()
                            ? InvocationResult.success(
                                    platform.getPlatformCode(), "web_search", outcome.response().answer(),
                                    outcome.latencyMs(), outcome.attemptCount())
                            : InvocationResult.failure(
                                    outcome.errorCategory() == null ? "INTERNAL_ERROR" : outcome.errorCategory().name(),
                                    outcome.errorMessage(), outcome.attemptCount(),
                                    new IllegalStateException(outcome.errorMessage()));
                } else {
                    invokeResult = invokeMonitoringWithRouter(platform, task, keyword.keywordText(), requestTimeoutMs);
                }
                PollResult detail = buildPollResult(batch, task, project, platform, keyword, invokeResult,
                        projectNames, judgeBrandNames, brand, siteDomains, normalizedPhones, contactTerms);
                pollShardPersistenceService.stagePollResult(item, detail);
                pollShardPersistenceService.upsertPollResultAndMarkItem(detail, item);
            }
            pollShardPersistenceService.markShardCompleted(shardId);
            pollAggregationService.tryAggregateBatch(shard.getBatchId());
        } catch (PollResultPersistenceBusyException ex) {
            pollShardPersistenceService.markShardResourceWaiting(shardId, ex.getMessage());
            throw ex;
        } catch (DispatchResourceBusyException ex) {
            pollShardPersistenceService.markShardResourceWaiting(shardId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            Long batchId = pollShardPersistenceService.markShardFailed(shardId, ex.getMessage());
            pollAggregationService.tryAggregateBatch(batchId);
            throw ex;
        }
    }

    private int countRemainingPollItems(List<PollBatchShardItem> items, int currentIndex) {
        int remaining = 0;
        for (int i = currentIndex; i < items.size(); i++) {
            PollBatchShardItem item = items.get(i);
            if (!isTerminalPollItem(item)) {
                remaining++;
            }
        }
        return Math.max(remaining, 1);
    }

    private boolean isTerminalPollItem(PollBatchShardItem item) {
        return "completed".equals(item.getStatus()) || "failed".equals(item.getStatus());
    }

    private Integer resolveMonitoringRequestTimeoutMs(AiPlatformConfig platform,
                                                      DispatchTask task,
                                                      int remainingItems) {
        int configuredTimeoutMs = dispatchProperties.getModelRequestTimeoutMs();
        configuredTimeoutMs = Math.min(configuredTimeoutMs, dispatchProperties.getQuestionPollRequestTimeoutCapMs());

        if (task == null || task.getTimeoutAt() == null) {
            return Math.max(configuredTimeoutMs, QUESTION_POLL_MIN_REQUEST_TIMEOUT_MS);
        }
        long remainingMs = java.time.Duration.between(LocalDateTime.now(), task.getTimeoutAt()).toMillis()
                - QUESTION_POLL_TASK_TIMEOUT_SAFETY_MS;
        if (remainingMs < QUESTION_POLL_MIN_REQUEST_TIMEOUT_MS) {
            return null;
        }
        int budgetedTimeoutMs = (int) Math.max(QUESTION_POLL_MIN_REQUEST_TIMEOUT_MS, remainingMs / Math.max(remainingItems, 1));
        return Math.min(configuredTimeoutMs, budgetedTimeoutMs);
    }

    private AiPlatformConfig resolveQuestionPollModelConfig(AiPlatformConfig platform) {
        if (platform == null) {
            return null;
        }
        if (!QUESTION_POLL_MODEL_TIER_LOW.equalsIgnoreCase(dispatchProperties.getQuestionPollModelTier())) {
            return platform;
        }
        if (!StringUtils.hasText(platform.getLowModelId())) {
            log.warn("Question poll low model requested but low_model_id is blank, platformCode={}", platform.getPlatformCode());
            return platform;
        }
        AiPlatformConfig copy = new AiPlatformConfig();
        BeanUtils.copyProperties(platform, copy);
        copy.setModelId(platform.getLowModelId().trim());
        return copy;
    }

    private PollResult buildPollResult(PollBatch batch,
                                       DispatchTask task,
                                       Project project,
                                       AiPlatformConfig platform,
                                       PollKeywordCandidate keyword,
                                       InvocationResult invokeResult,
                                       Set<String> projectNames,
                                       Set<String> judgeBrandNames,
                                       Brand brand,
                                       Set<String> siteDomains,
                                       Set<String> normalizedPhones,
                                       Set<String> contactTerms) {
        MatchInfo match = invokeResult.success
                ? analyzeMatch(projectNames, siteDomains, normalizedPhones, contactTerms, invokeResult.responseText)
                : MatchInfo.empty();
        JudgeInfo judge = invokeResult.success
                ? judgeEffectiveHitIfNeeded(judgeBrandNames, brand, siteDomains, normalizedPhones, contactTerms, keyword.keywordText(), invokeResult.responseText)
                : JudgeInfo.skipped("model invocation failed");
        boolean hit = match.hit || Boolean.TRUE.equals(judge.effectiveHit);
        boolean contactMentioned = match.contactMentioned || Boolean.TRUE.equals(judge.contactMentioned);
        int contactMentionCount = match.contactMentionCount > 0
                ? match.contactMentionCount
                : (Boolean.TRUE.equals(judge.contactMentioned) ? 1 : 0);

        String recordType;
        if (!invokeResult.success) {
            recordType = "error";
        } else if (hit) {
            recordType = "hit";
        } else {
            recordType = "miss";
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("keyword_text", redactSensitive(keyword.keywordText()));
        detail.put("response_time_ms", invokeResult.responseTimeMs);
        detail.put("platform_code", platform.getPlatformCode());
        detail.put("channel", invokeResult.channel);
        if (invokeResult.success) {
            detail.put("platform_response", redactSensitive(invokeResult.responseText));
            Map<String, Object> matchDetails = new LinkedHashMap<>();
            matchDetails.put("is_hit", hit);
            matchDetails.put("rule_hit", match.hit);
            matchDetails.put("match_type", match.matchType);
            matchDetails.put("site_mentioned", match.siteMentioned);
            matchDetails.put("contact_mentioned", contactMentioned);
            matchDetails.put("contact_mention_count", contactMentionCount);
            detail.put("match_details", matchDetails);
            detail.put("judge_details", judge.toPayload());
        } else {
            Map<String, Object> errorPayload = new LinkedHashMap<>();
            errorPayload.put("error_code", invokeResult.errorCode);
            errorPayload.put("error_message", redactSensitive(invokeResult.errorMessage));
            errorPayload.put("raw_response_snippet", redactSensitive(invokeResult.responseText));
            detail.put("error_payload", errorPayload);
        }

        PollResult result = buildPollResultIdentity(batch, task, project, platform, keyword);
        result.setStatus(invokeResult.success ? "completed" : "failed");
        result.setRequestCount(invokeResult.requestCount);
        result.setResponseTimeMs(invokeResult.responseTimeMs);
        result.setIsHit(hit);
        result.setEffectiveHit(judge.effectiveHit);
        result.setMatchType(match.matchType);
        result.setSiteMentioned(match.siteMentioned);
        result.setContactMentioned(contactMentioned);
        result.setContactMentionCount(contactMentionCount);
        result.setJudgeStatus(judge.status);
        result.setHitLevel(judge.hitLevel);
        result.setHitSentiment(judge.sentiment);
        result.setMentionType(judge.mentionType);
        result.setJudgeEvidence(judge.evidence);
        result.setJudgeRiskReason(judge.riskReason);
        result.setJudgeModel(judge.judgeModel);
        result.setJudgeAt(judge.judgeAt);
        result.setJudgeError(judge.error);
        result.setRecordType(recordType);
        result.setDetailJson(JSONUtil.toJsonStr(detail));
        return result;
    }

    private PollResult buildPollResultIdentity(PollBatch batch,
                                               DispatchTask task,
                                               Project project,
                                               AiPlatformConfig platform,
                                               PollKeywordCandidate keyword) {
        PollResult result = new PollResult();
        result.setBatchId(batch.getId());
        result.setDispatchTaskId(task.getId());
        result.setProjectId(project.getId());
        result.setKeywordResultId(keyword.keywordResultId());
        result.setKeywordTextSnapshot(keyword.keywordText());
        result.setPlatformId(platform.getId());
        result.setPlatformCode(platform.getPlatformCode());
        result.setChannelCode(StringUtils.hasText(platform.getChannelCode())
                ? platform.getChannelCode() : platform.getPlatformCode());
        result.setTriggerType(resolvePollTriggerType(batch).name());
        result.setBatchDate(batch.getBatchDate());
        result.setBatchNo(batch.getBatchNo());
        result.setQuestionTier(batch.getQuestionTier());
        return result;
    }

    private boolean isWebSearchPlatform(AiPlatformConfig platform) {
        if (platform == null || !StringUtils.hasText(platform.getIntegrationType())) {
            return false;
        }
        try {
            return IntegrationType.valueOf(platform.getIntegrationType()).isWebSearch();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private TriggerType resolvePollTriggerType(PollBatch batch) {
        if (batch != null && StringUtils.hasText(batch.getTriggerType())) {
            try {
                return TriggerType.valueOf(batch.getTriggerType());
            } catch (IllegalArgumentException ignored) {
                // Preserve compatibility with pre-web batches.
            }
        }
        return TriggerType.SCHEDULED;
    }

    private void executeContentGeneration(DispatchTask task, List<AiPlatformConfig> platformConfigs) {
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        if (!"active".equals(project.getStatus()) || Boolean.FALSE.equals(project.getContentGenerationEnabled())) {
            log.info("Skip CONTENT_GENERATION task {}, project inactive or disabled", task.getId());
            return;
        }

        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(project.getCompanyId());
        List<PackageContentConfig> configs = packageContentConfigMapper.selectList(
                new LambdaQueryWrapper<PackageContentConfig>()
                        .eq(PackageContentConfig::getPackageType, binding.getPackageType())
                        .eq(PackageContentConfig::getIsActive, true)
                        .orderByAsc(PackageContentConfig::getArticleType)
        );
        if (configs.isEmpty()) {
            log.info("Skip CONTENT_GENERATION task {}, no config for package {}", task.getId(), binding.getPackageType());
            return;
        }
        geoPromptBuilder.ensureHasSavedKeywords(project.getId());

        LocalDate batchDate = resolveBatchDate(task);
        int batchNo = resolveBatchNo(task);
        if (!hasContentGenerationMetadata(task)) {
            log.warn("Skip legacy CONTENT_GENERATION task {}, missing channel/period/slot metadata", task.getId());
            return;
        }
        String targetChannel = resolveTargetChannel(task);
        Integer generationSlotNo = resolveGenerationSlotNo(task);
        String periodType = resolvePeriodType(task);
        String periodKey = QuotaPeriodResolver.periodKey(periodType, task.getWindowStart());
        ArticleGenerationWindowLockService.WindowLock windowLock =
                articleGenerationWindowLockService.tryLock(project.getId(), targetChannel, periodKey, generationSlotNo);
        if (windowLock == null) {
            throw new DispatchResourceBusyException(
                    "Article generation window is busy: project=" + project.getId()
                            + ", channel=" + targetChannel
                            + ", periodKey=" + periodKey
                            + ", generationSlotNo=" + generationSlotNo,
                    null
            );
        }
        try (windowLock) {
            ArticleBatch batch = articleGenerationPersistenceService.ensureArticleBatch(
                    task.getId(), project.getId(), batchDate, batchNo, targetChannel, generationSlotNo);

            Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());

            int total = 0;
            int completed = 0;
            int failed = 0;
            int articleIndex = Math.max(generationSlotNo == null ? 1 : generationSlotNo, 1) - 1;
            int platformIndex = articleIndex;
            PackageContentConfig cfg = configs.get(Math.floorMod(articleIndex, configs.size()));
            total++;
            try {
                GeoPromptBuilder.PromptPair prompt = geoPromptBuilder.buildContentPrompt(
                        project, brand, cfg.getArticleType(), articleIndex
                );
                String articleAngle = geoPromptBuilder.resolveArticleAngle(project, articleIndex);
                InvocationResult result = invokeArticleContentWithRouter(
                        platformConfigs,
                        task,
                        prompt.systemPrompt(),
                        prompt.userPrompt(),
                        platformIndex
                );
                if (!result.success) {
                    failed++;
                    log.warn("CONTENT_GENERATION failed task={}, type={}, err={}", task.getId(), cfg.getArticleType(), result.errorMessage);
                } else {
                    String content = normalizeGeneratedContent(result.responseText);
                    String title = extractGeneratedTitle(content, cfg.getArticleType(), project.getProjectName());
                    Map<String, Object> promptSnapshot = new LinkedHashMap<>();
                    promptSnapshot.put("articleType", cfg.getArticleType());
                    promptSnapshot.put("targetChannel", targetChannel);
                    promptSnapshot.put("generationSlotNo", generationSlotNo);
                    promptSnapshot.put("systemPrompt", prompt.systemPrompt());
                    promptSnapshot.put("userPrompt", prompt.userPrompt());
                    Map<String, Object> inputSnapshot = new LinkedHashMap<>();
                    inputSnapshot.put("projectName", project.getProjectName());
                    inputSnapshot.put("packageType", binding.getPackageType());
                    inputSnapshot.put("source", "keyword_group");
                    inputSnapshot.put("targetChannel", targetChannel);
                    inputSnapshot.put("periodType", periodType);
                    inputSnapshot.put("periodKey", periodKey);
                    inputSnapshot.put("generationSlotNo", generationSlotNo);
                    articleGenerationPersistenceService.persistGeneratedArticle(
                            batch.getId(),
                            project,
                            cfg.getArticleType(),
                            title,
                            content,
                            JSONUtil.toJsonStr(promptSnapshot),
                            JSONUtil.toJsonStr(inputSnapshot),
                            result.platformCode,
                            resolveModelIdByPlatform(result.platformCode),
                            articleAngle,
                            targetChannel,
                            periodType,
                            periodKey,
                            generationSlotNo
                    );
                    completed++;
                }
            } catch (DispatchResourceBusyException ex) {
                throw ex;
            } catch (Exception ex) {
                failed++;
                log.warn("CONTENT_GENERATION article failed task={}, type={}, articleIndex={}, err={}",
                        task.getId(), cfg.getArticleType(), articleIndex, ex.getMessage(), ex);
            }
            articleGenerationPersistenceService.completeBatch(batch.getId(), total, completed, failed);
            if (completed <= 0 && failed > 0) {
                throw new BizException(500, "Content generation failed for all articles");
            }
        }
    }

    private void executeBrandStatementGeneration(DispatchTask task, List<AiPlatformConfig> platformConfigs) {
        Map<String, Object> payload = parsePayload(task);
        Long brandId = parseLong(payload.get("brandId"));
        Project project = projectMapper.selectById(task.getProjectId());
        if (brandId == null && project != null) {
            brandId = project.getBrandId();
        }
        if (brandId == null) {
            throw new BizException(400, "Missing brandId in brand statement task payload");
        }
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException(404, "Brand not found");
        }
        String prompt = buildBrandStatementPrompt(project, brand);
        InvocationResult result = invokeWithOrderedPlatforms(platformConfigs, task, prompt, 0);
        if (!result.success) {
            throw new BizException(500, "Brand statement generation failed: " + result.errorMessage);
        }
        String statementJson = parseGeneratedStatement(result.responseText);
        brandStatementService.applyAutoGeneratedStatement(brandId, statementJson);
    }

    private InvocationResult invokeWithOrderedPlatforms(List<AiPlatformConfig> platformConfigs, DispatchTask task, String prompt, int cursor) {
        Exception lastError = null;
        for (int i = 0; i < platformConfigs.size(); i++) {
            AiPlatformConfig cfg = platformConfigs.get((cursor + i) % platformConfigs.size());
            InvocationResult result = invokeWithFallback(cfg, task, prompt);
            if (result.success) {
                return result;
            }
            lastError = result.error;
        }
        return InvocationResult.failure("ALL_FAILED", lastError == null ? "all failed" : lastError.getMessage(), 0, lastError);
    }

    private InvocationResult invokeContentWithOrderedPlatforms(List<AiPlatformConfig> platformConfigs,
                                                               DispatchTask task,
                                                               String systemPrompt,
                                                               String userPrompt,
                                                               int cursor) {
        Exception lastError = null;
        for (int i = 0; i < platformConfigs.size(); i++) {
            AiPlatformConfig cfg = platformConfigs.get((cursor + i) % platformConfigs.size());
            InvocationResult result = invokeContentWithFallback(cfg, task, systemPrompt, userPrompt);
            if (result.success) {
                return result;
            }
            lastError = result.error;
        }
        return InvocationResult.failure("ALL_FAILED", lastError == null ? "all failed" : lastError.getMessage(), 0, lastError);
    }

    private InvocationResult invokeArticleContentWithRouter(List<AiPlatformConfig> platformConfigs,
                                                            DispatchTask task,
                                                            String systemPrompt,
                                                            String userPrompt,
                                                            int cursor) {
        try {
            LlmRouteResult routed = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.ARTICLE,
                    systemPrompt,
                    userPrompt,
                    0.7D,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    dispatchProperties.getModelRequestTimeoutMs(),
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    0,
                    null,
                    false,
                    1000,
                    cursor,
                    platformConfigs
            ))).routeResult();
            task.setPlatformCode(routed.platformCode());
            task.setCurrentChannel(routed.channel());
            log.info("Content generation task {} executed by platform={}, model={}, channel={}",
                    task.getId(), routed.platformCode(), routed.modelId(), routed.channel());
            return InvocationResult.success(
                    routed.platformCode(),
                    routed.channel(),
                    routed.responseText(),
                    Math.max(routed.durationMs(), 1L),
                    routed.requestCount()
            );
        } catch (LlmRouteException ex) {
            LlmCapacityFailure capacityFailure = classifyCapacityFailure(ex);
            if (capacityFailure != null) {
                throw new DispatchResourceBusyException(ex.getMessage(), ex, capacityFailure);
            }
            return InvocationResult.failure(ex.failureKind().name(), ex.getMessage(), ex.requestCount(), ex);
        } catch (com.huanjing.geo.common.llm.LlmInvokeException ex) {
            return InvocationResult.failure("ALL_FAILED", ex.getMessage(), 0, ex);
        }
    }

    private InvocationResult invokeMonitoringWithRouter(AiPlatformConfig platform,
                                                        DispatchTask task,
                                                        String questionText,
                                                        int requestTimeoutMs) {
        try {
            LlmRouteResult routed = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.MONITORING,
                    "You are a GEO (Generative Engine Optimization) monitoring assistant.",
                    questionText,
                    0D,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    requestTimeoutMs,
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    null,
                    null,
                    false,
                    1000,
                    0,
                    List.of(platform)
            ))).routeResult();
            task.setPlatformCode(routed.platformCode());
            task.setCurrentChannel(routed.channel());
            log.info("QUESTION_POLL task {} executed by platform={}, model={}, channel={}",
                    task.getId(), routed.platformCode(), routed.modelId(), routed.channel());
            return InvocationResult.success(
                    routed.platformCode(),
                    routed.channel(),
                    routed.responseText(),
                    Math.max(routed.durationMs(), 1L),
                    routed.requestCount()
            );
        } catch (LlmRouteException ex) {
            LlmCapacityFailure capacityFailure = classifyCapacityFailure(ex);
            if (capacityFailure != null) {
                throw new DispatchResourceBusyException(ex.getMessage(), ex, capacityFailure);
            }
            return InvocationResult.failure(ex.failureKind().name(), ex.getMessage(), ex.requestCount(), ex);
        } catch (com.huanjing.geo.common.llm.LlmInvokeException ex) {
            return InvocationResult.failure("ALL_FAILED", ex.getMessage(), 0, ex);
        }
    }

    private boolean isCapacityFailure(LlmRouteFailureKind failureKind) {
        return failureKind == LlmRouteFailureKind.ALL_RATE_LIMITED
                || failureKind == LlmRouteFailureKind.ALL_PERMIT_BUSY
                || failureKind == LlmRouteFailureKind.ALL_CIRCUIT_OPEN;
    }

    private LlmCapacityFailure classifyCapacityFailure(LlmRouteException ex) {
        if (dispatchProperties.isCapacityFailureClassificationEnabled()) {
            return capacityFailureClassifier.classify(ex).orElse(null);
        }
        if (isCapacityFailure(ex.failureKind())) {
            return new LlmCapacityFailure(null, null, ex.failureKind().name(), ex.getClass().getSimpleName());
        }
        return null;
    }

    private String buildBrandInfo(Project project, Brand brand) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project Name: ").append(Optional.ofNullable(project.getProjectName()).orElse("")).append("\n");
        sb.append("Project Aliases: ").append(Optional.ofNullable(project.getProjectAliases()).orElse("")).append("\n");
        List<String> targetRegions = parseStringList(project.getTargetRegions());
        if (!targetRegions.isEmpty()) {
            sb.append("Target Regions: ").append(String.join(", ", targetRegions)).append("\n");
        }
        if (StringUtils.hasText(project.getTargetAudience())) {
            sb.append("Target Audience: ").append(project.getTargetAudience().trim()).append("\n");
        }
        if (StringUtils.hasText(project.getContentTone())) {
            sb.append("Content Tone: ").append(project.getContentTone().trim()).append("\n");
        }
        List<String> preferredAngles = parseStringList(project.getPreferredAngles());
        if (!preferredAngles.isEmpty()) {
            sb.append("Preferred Angles: ").append(String.join(", ", preferredAngles)).append("\n");
        }
        if (StringUtils.hasText(project.getContentNote())) {
            sb.append("Content Note: ").append(project.getContentNote().trim()).append("\n");
        }
        if (brand != null) {
            sb.append("Brand Name: ").append(Optional.ofNullable(brand.getBrandName()).orElse("")).append("\n");
            sb.append("Main Business: ").append(Optional.ofNullable(brand.getMainBusiness()).orElse("")).append("\n");
            String promptStatement = StringUtils.hasText(project.getCustomStatement())
                    ? project.getCustomStatement().trim()
                    : brandStatementService.resolvePromptStatement(brand);
            if (StringUtils.hasText(promptStatement)) {
                sb.append("Brand Statement: ").append(promptStatement).append("\n");
            }
            sb.append("Brand Description: ").append(Optional.ofNullable(brand.getDescription()).orElse("")).append("\n");
            sb.append("Public Phone: ").append(Optional.ofNullable(brand.getPublicPhone()).orElse("")).append("\n");
            sb.append("Public Address: ").append(Optional.ofNullable(brand.getPublicAddress()).orElse("")).append("\n");
        }
        return sb.toString();
    }

    private String buildBrandStatementPrompt(Project project, Brand brand) {
        String projectName = project == null ? "" : Optional.ofNullable(project.getProjectName()).orElse("");
        String projectAliases = project == null ? "" : Optional.ofNullable(project.getProjectAliases()).orElse("");
        return "You are a GEO (Generative Engine Optimization) content strategist. Generate a structured brand statement JSON with keys: "
                + "positioning, selling_points, differentiation, brand_paragraph.\n"
                + "Rules:\n"
                + "- positioning: <=20 Chinese characters\n"
                + "- selling_points: array with 3-5 concise strings\n"
                + "- differentiation: compare with competitors, avoid absolute claims\n"
                + "- brand_paragraph: around 200 Chinese characters\n"
                + "- output JSON only, no markdown, no explanation.\n\n"
                + "Input:\n"
                + "project_name=" + projectName + "\n"
                + "project_aliases=" + projectAliases + "\n"
                + "brand_name=" + Optional.ofNullable(brand.getBrandName()).orElse("") + "\n"
                + "brand_aliases=" + Optional.ofNullable(brand.getBrandSlug()).orElse("") + "\n"
                + "main_business=" + Optional.ofNullable(brand.getMainBusiness()).orElse("") + "\n"
                + "service_area=" + Optional.ofNullable(brand.getServiceArea()).orElse("") + "\n"
                + "website=" + Optional.ofNullable(brand.getWebsite()).orElse("") + "\n"
                + "competitors=" + (project == null ? "" : Optional.ofNullable(project.getRemark()).orElse("")) + "\n";
    }

    private String parseGeneratedStatement(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            throw new BizException(500, "Brand statement response is empty");
        }
        JSONObject json;
        try {
            String raw = responseText.trim();
            int first = raw.indexOf('{');
            int last = raw.lastIndexOf('}');
            if (first >= 0 && last > first) {
                raw = raw.substring(first, last + 1);
            }
            json = JSONUtil.parseObj(raw);
        } catch (Exception ex) {
            throw new BizException(500, "Brand statement response is not valid JSON");
        }
        String positioning = json.getStr("positioning");
        String differentiation = json.getStr("differentiation");
        String brandParagraph = json.getStr("brand_paragraph");
        List<String> sellingPoints = new ArrayList<>();
        Object sellingPointsRaw = json.get("selling_points");
        if (sellingPointsRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    sellingPoints.add(String.valueOf(item).trim());
                }
            }
        } else if (sellingPointsRaw instanceof String text && StringUtils.hasText(text)) {
            Arrays.stream(text.split("[,，、\\n\\r]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(sellingPoints::add);
        }
        if (!StringUtils.hasText(positioning) || !StringUtils.hasText(differentiation) || !StringUtils.hasText(brandParagraph)) {
            throw new BizException(500, "Brand statement JSON missing required fields");
        }
        if (sellingPoints.isEmpty()) {
            throw new BizException(500, "Brand statement JSON missing selling_points");
        }
        return brandStatementService.buildStructuredStatementJson(positioning, sellingPoints, differentiation, brandParagraph);
    }

    private List<String> parseStringList(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            JSONUtil.parseArray(rawJson).forEach(it -> {
                if (it != null && StringUtils.hasText(String.valueOf(it))) {
                    result.add(String.valueOf(it).trim());
                }
            });
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String resolveForbiddenPhrasePrompt(Long projectId) {
        Set<String> words = new LinkedHashSet<>();
        if (projectId != null) {
            Project project = projectMapper.selectById(projectId);
            if (project != null && project.getBrandId() != null) {
                Brand brand = brandMapper.selectById(project.getBrandId());
                if (brand != null && StringUtils.hasText(brand.getForbiddenPhrases())) {
                    words.addAll(Arrays.stream(brand.getForbiddenPhrases().split("[,，、;；\\n\\r]+"))
                            .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toSet()));
                }
            }
        }
        List<SysDictItem> globals = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "global_forbidden_phrase")
                        .eq(SysDictItem::getEnabled, true)
        );
        for (SysDictItem item : globals) {
            if (StringUtils.hasText(item.getDictKey())) {
                words.add(item.getDictKey().trim());
            }
        }
        List<String> effectiveWords = ArticleForbiddenPhrasePolicy.effectivePhrases(words);
        if (effectiveWords.isEmpty()) {
            return "无";
        }
        return String.join("、", effectiveWords);
    }

    private String normalizeGeneratedContent(String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return "## 未生成有效内容";
        }
        return responseText.trim();
    }

    private String extractGeneratedTitle(String content, String articleType, String projectName) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("#")) {
                String title = t.replaceFirst("^#+\\s*", "").trim();
                if (StringUtils.hasText(title)) {
                    return title.length() > 255 ? title.substring(0, 255) : title;
                }
            }
        }
        for (String line : lines) {
            String t = line.trim();
            if (StringUtils.hasText(t)) {
                return t.length() > 255 ? t.substring(0, 255) : t;
            }
        }
        return projectName + "-" + articleType + "-" + LocalDate.now();
    }

    private String resolveModelIdByPlatform(String platformCode) {
        if (!StringUtils.hasText(platformCode)) {
            return null;
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, platformCode)
                        .last("LIMIT 1")
        );
        return config == null ? null : config.getModelId();
    }

    private void upsertPollResult(PollResult result) {
        withTransientDbRetry("upsert poll result", () -> {
            LambdaQueryWrapper<PollResult> wrapper = new LambdaQueryWrapper<PollResult>()
                    .eq(PollResult::getProjectId, result.getProjectId())
                    .eq(PollResult::getPlatformId, result.getPlatformId())
                    .eq(PollResult::getBatchDate, result.getBatchDate())
                    .eq(PollResult::getBatchNo, result.getBatchNo())
                    .eq(PollResult::getQuestionTier, result.getQuestionTier())
                    .last("LIMIT 1");
            wrapper.eq(PollResult::getKeywordResultId, result.getKeywordResultId());
            PollResult existing = pollResultMapper.selectOne(wrapper);
            if (existing == null) {
                pollResultMapper.insert(result);
                return null;
            }
            result.setId(existing.getId());
            pollResultMapper.updateById(result);
            return null;
        });
    }

    private <T> T withTransientDbRetry(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            if (!isTransientConnectionFailure(ex)) {
                throw ex;
            }
            log.warn("Transient DB connection failure during {}, retrying once: {}", operation, ex.getMessage());
            sleepBeforeDbRetry();
            return action.get();
        }
    }

    private boolean isTransientConnectionFailure(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 8; depth++) {
            String className = current.getClass().getName();
            if (className.contains("CommunicationsException")
                    || className.contains("SQLTransientConnectionException")
                    || className.contains("SQLNonTransientConnectionException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("Communications link failure")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeDbRetry() {
        try {
            Thread.sleep(DB_TRANSIENT_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before DB retry", ex);
        }
    }

    private LocalDate resolveBatchDate(DispatchTask task) {
        Map<String, Object> payload = parsePayload(task);
        Object batchDate = payload.get("batchDate");
        if (batchDate != null && StringUtils.hasText(String.valueOf(batchDate))) {
            return LocalDate.parse(String.valueOf(batchDate));
        }
        if (task.getWindowEnd() != null) {
            return task.getWindowEnd();
        }
        if (task.getDueTime() != null) {
            return task.getDueTime().toLocalDate();
        }
        return LocalDate.now();
    }

    private int resolveBatchNo(DispatchTask task) {
        Map<String, Object> payload = parsePayload(task);
        Object batchNo = payload.get("batchNo");
        if (batchNo == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(batchNo)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String resolveQuestionTier(DispatchTask task) {
        Map<String, Object> payload = parsePayload(task);
        Object value = payload.get("questionTier");
        String tier = value == null ? "A" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (!QUESTION_TIERS.contains(tier)) {
            throw new BizException(400, "Invalid question tier for QUESTION_POLL: " + value);
        }
        return tier;
    }

    private Long resolveShardId(DispatchTask task) {
        Object value = parsePayload(task).get("shardId");
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BizException(400, "Invalid question poll shardId: " + value);
        }
    }

    private int resolveTierPollLimit(Project project, String questionTier) {
        Integer limit = switch (questionTier) {
            case "A" -> project.getPlanKeywordGroupLimitA();
            case "B" -> project.getPlanKeywordGroupLimitB();
            case "C" -> project.getPlanKeywordGroupLimitC();
            default -> null;
        };
        if (limit != null && limit > 0) {
            return limit;
        }
        if ("A".equals(questionTier) && project.getPlanKeywordGroupLimit() != null && project.getPlanKeywordGroupLimit() > 0) {
            return project.getPlanKeywordGroupLimit();
        }
        return 0;
    }

    private boolean hasContentGenerationMetadata(DispatchTask task) {
        return task.getWindowStart() != null
                && StringUtils.hasText(resolveTargetChannel(task))
                && StringUtils.hasText(resolvePeriodType(task))
                && resolveGenerationSlotNo(task) != null;
    }

    private String resolveTargetChannel(DispatchTask task) {
        if (StringUtils.hasText(task.getTargetChannel())) {
            return task.getTargetChannel().trim();
        }
        Object value = parsePayload(task).get("targetChannel");
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value).trim();
        }
        return null;
    }

    private String resolvePeriodType(DispatchTask task) {
        Object value = parsePayload(task).get("periodType");
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return String.valueOf(value).trim();
        }
        return null;
    }

    private Integer resolveGenerationSlotNo(DispatchTask task) {
        if (task.getGenerationSlotNo() != null && task.getGenerationSlotNo() > 0) {
            return task.getGenerationSlotNo();
        }
        Object value = parsePayload(task).get("generationSlotNo");
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(String.valueOf(value)));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> parsePayload(DispatchTask task) {
        if (!StringUtils.hasText(task.getPayloadJson())) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            JSONUtil.parseObj(task.getPayloadJson()).forEach((key, value) -> payload.put(String.valueOf(key), value));
            return payload;
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private int priorityRank(String priority) {
        String normalized = normalizePriority(priority);
        if ("A".equals(normalized)) {
            return 0;
        }
        if ("B".equals(normalized)) {
            return 1;
        }
        if ("C".equals(normalized)) {
            return 2;
        }
        return 9;
    }

    private String normalizePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return "";
        }
        return priority.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> resolveProjectNameSet(Project project) {
        Set<String> names = new LinkedHashSet<>();
        if (StringUtils.hasText(project.getProjectName())) {
            names.add(project.getProjectName().trim());
        }
        if (StringUtils.hasText(project.getProjectAliases())) {
            Arrays.stream(project.getProjectAliases().split("[,\\uFF0C\\u3001\\n\\r]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(names::add);
        }
        return names;
    }

    private Set<String> resolveJudgeBrandNameSet(Project project, Brand brand) {
        Set<String> names = new LinkedHashSet<>();
        if (brand != null) {
            if (StringUtils.hasText(brand.getBrandName())) {
                names.add(brand.getBrandName().trim());
            }
            if (StringUtils.hasText(brand.getBrandShortName())) {
                names.add(brand.getBrandShortName().trim());
            }
            if (StringUtils.hasText(brand.getBrandSlug())) {
                names.add(brand.getBrandSlug().trim());
            }
        }
        if (project != null) {
            if (StringUtils.hasText(project.getBrandName())) {
                names.add(project.getBrandName().trim());
            }
            if (StringUtils.hasText(project.getProjectAliases())) {
                Arrays.stream(project.getProjectAliases().split("[,，、;；\\n\\r]+"))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .forEach(names::add);
            }
            names.addAll(parseStringList(project.getPollBrandAliasesJson()));
        }
        if (names.isEmpty() && project != null && StringUtils.hasText(project.getProjectName())) {
            names.add(project.getProjectName().trim());
        }
        names.removeIf(name -> !StringUtils.hasText(name) || name.trim().length() < 2);
        return names;
    }

    private Set<String> resolveSiteDomains(Project project) {
        Set<String> domains = new HashSet<>();
        Company company = project.getCompanyId() == null ? null : companyMapper.selectById(project.getCompanyId());
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (company != null) {
            String domain = extractDomain(company.getOfficialWebsite());
            if (domain != null) {
                domains.add(domain);
            }
        }
        if (brand != null) {
            String domain = extractDomain(brand.getWebsite());
            if (domain != null) {
                domains.add(domain);
            }
        }
        return domains;
    }

    private Set<String> resolvePhones(Project project) {
        Set<String> phones = new HashSet<>();
        Company company = project.getCompanyId() == null ? null : companyMapper.selectById(project.getCompanyId());
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (company != null && StringUtils.hasText(company.getContactPhone())) {
            phones.add(normalizePhone(company.getContactPhone()));
        }
        if (brand != null && StringUtils.hasText(brand.getPhone())) {
            phones.add(normalizePhone(brand.getPhone()));
        }
        if (brand != null && StringUtils.hasText(brand.getPublicPhone())) {
            phones.add(normalizePhone(brand.getPublicPhone()));
        }
        phones.removeIf(v -> !StringUtils.hasText(v));
        return phones;
    }

    private Set<String> resolveContactTerms(Project project) {
        Set<String> terms = new HashSet<>();
        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (brand != null && StringUtils.hasText(brand.getPublicAddress())) {
            terms.add(brand.getPublicAddress().trim());
        }
        terms.removeIf(v -> !StringUtils.hasText(v));
        return terms;
    }

    private JudgeInfo judgeEffectiveHitIfNeeded(Set<String> judgeBrandNames,
                                                Brand brand,
                                                Set<String> siteDomains,
                                                Set<String> phones,
                                                Set<String> contactTerms,
                                                String questionText,
                                                String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return JudgeInfo.failed("ANSWER_EMPTY", "大模型回答为空");
        }
        try {
            LlmRouteResult routed = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.MONITORING,
                    effectiveHitJudgeSystemPrompt(),
                    buildEffectiveHitJudgeUserPrompt(brand, judgeBrandNames, siteDomains, phones, contactTerms, questionText, responseText),
                    0D,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    dispatchProperties.getModelRequestTimeoutMs(),
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    0,
                    800,
                    true,
                    300,
                    0,
                    List.of()
            ))).routeResult();
            return parseJudgeInfo(routed.responseText(), routed.platformCode() + "/" + routed.modelId());
        } catch (LlmRouteException ex) {
            log.warn("Effective hit judge failed, question={}, reason={}", redactSensitive(questionText), ex.getMessage());
            return JudgeInfo.failed(ex.failureKind().name(), ex.getMessage());
        } catch (com.huanjing.geo.common.llm.LlmInvokeException ex) {
            log.warn("Effective hit judge failed, question={}, reason={}", redactSensitive(questionText), ex.getMessage());
            return JudgeInfo.failed("ALL_FAILED", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Effective hit judge parse failed, question={}", redactSensitive(questionText), ex);
            return JudgeInfo.failed("JUDGE_PARSE_FAILED", ex.getMessage());
        }
    }

    private boolean questionContainsBrandName(String questionText, Set<String> judgeBrandNames) {
        if (!StringUtils.hasText(questionText) || judgeBrandNames == null || judgeBrandNames.isEmpty()) {
            return false;
        }
        String raw = questionText.trim();
        String normalizedQuestion = normalizeTextForMatch(raw);
        for (String name : judgeBrandNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            if (raw.contains(trimmed)) {
                return true;
            }
            String normalizedName = normalizeTextForMatch(trimmed);
            if (StringUtils.hasText(normalizedName) && normalizedQuestion.contains(normalizedName)) {
                return true;
            }
        }
        return false;
    }

    private String effectiveHitJudgeSystemPrompt() {
        return """
                你是一个 GEO（生成式引擎优化）品牌可见度评估裁判。你的任务是判断“大模型回答”中，当前品牌是否形成了有效命中。
                请严格基于输入内容判断，不要引入外部知识，不要补充事实，不要推测品牌真实情况。
                只输出 JSON，不要输出 Markdown，不要输出解释性前后缀。
                """;
    }

    private String buildEffectiveHitJudgeUserPrompt(Brand brand,
                                                    Set<String> judgeBrandNames,
                                                    Set<String> siteDomains,
                                                    Set<String> phones,
                                                    Set<String> contactTerms,
                                                    String questionText,
                                                    String responseText) {
        String brandName = brand != null && StringUtils.hasText(brand.getBrandName())
                ? brand.getBrandName().trim()
                : judgeBrandNames.stream().findFirst().orElse("");
        String brandAliases = judgeBrandNames.stream()
                .filter(name -> !name.equals(brandName))
                .collect(Collectors.joining("、"));
        List<String> contactItems = new ArrayList<>();
        if (phones != null) {
            contactItems.addAll(phones);
        }
        if (contactTerms != null) {
            contactItems.addAll(contactTerms);
        }
        String contacts = contactItems.stream().filter(StringUtils::hasText).collect(Collectors.joining("、"));

        return """
                【当前品牌】
                品牌名称：%s
                品牌别名：%s
                官网域名：%s
                联系方式：%s

                【用户问题】
                %s

                【大模型回答】
                %s

                【有效命中定义】
                满足以下条件之一，可判定为有效命中：
                1. 回答明确提到当前品牌名称或品牌别名，并将其作为可选择、可了解、可咨询、可比较、可参考的对象。
                2. 回答虽然没有强推荐，但在品牌列表、方案对比、服务商推荐、厂家推荐、产品推荐、选型建议中，正常列出了当前品牌。
                3. 回答提到当前品牌，并给出了与品牌相关的正向、中性或事实性信息，例如业务范围、产品服务、官网、电话、地址、案例、资质、优势、适用场景。
                4. 回答提到当前品牌官网、电话、地址等可识别联系方式，且上下文没有明显否定、排除或警示含义。

                【无效命中定义】
                出现以下情况，应判定为无效命中：
                1. 回答只是复述用户问题中的品牌名称，没有提供任何关于该品牌的回答内容。
                2. 回答明确否定、排除、不推荐当前品牌，例如“不建议选择”“不是该领域合适选择”“没有相关信息”“无法确认该品牌”等。
                3. 回答提到当前品牌只是作为负面示例、风险提示、投诉对象、反例或对比中的劣势方。
                4. 回答中品牌名称出现在免责声明、无法验证说明、引用用户原问题、搜索建议、占位内容中，而不是实际推荐或介绍内容。
                5. 回答内容主要推荐其他品牌，当前品牌只是被动带过，且没有形成可供用户选择、了解、咨询或比较的信息。
                6. 回答出现疑似同名误判：提到的名称虽然相同或相近，但上下文明显不是当前品牌、公司、产品或服务。
                7. 回答完全没有提到当前品牌名称、品牌别名、官网域名或联系方式。

                【推荐强度分级】
                hitLevel 只能是：strong / normal / weak / invalid。
                strong：强有效命中。回答明确推荐、优先推荐、重点介绍当前品牌，或将其列为主要选择之一。
                normal：普通有效命中。回答正常列出或介绍当前品牌，但没有明显优先推荐。
                weak：弱有效命中。回答轻微提及当前品牌，有一定可识别信息，但推荐或介绍较弱。
                invalid：无效命中。不满足有效命中定义。

                【情感倾向】
                sentiment 只能是：positive / neutral / negative / unknown。

                【mentionType】
                mentionType 只能是：
                recommendation / list_inclusion / factual_mention / contact_mention / question_echo / negative_mention / irrelevant / not_mentioned。

                【联系方式曝光判断】
                contactMentioned 只能是 true 或 false。
                当回答在当前品牌上下文中提到电话、地址、官网、在线客服、门店咨询、联系商家、预约咨询、到店咨询、官方渠道咨询等联系方式或联系渠道时，contactMentioned=true。
                如果回答只是泛泛说“可以了解”“可以比较”，没有联系动作、联系渠道、联系方式或咨询入口，则 contactMentioned=false。
                contactMentionType 只能是：phone / address / website / online_service / store_visit / generic_contact / none。
                contactEvidence 用一句话说明判定依据；没有联系方式曝光时返回空字符串。

                【输出 JSON 格式】
                {
                  "effectiveHit": true,
                  "hitLevel": "strong",
                  "sentiment": "positive",
                  "mentionType": "recommendation",
                  "contactMentioned": true,
                  "contactMentionType": "generic_contact",
                  "contactEvidence": "回答中提到可咨询当前品牌的官方渠道。",
                  "evidence": "回答中将当前品牌列为推荐厂家，并说明了可咨询或可选择的理由。",
                  "riskReason": ""
                }
                """.formatted(
                brandName,
                StringUtils.hasText(brandAliases) ? brandAliases : "无",
                siteDomains == null || siteDomains.isEmpty() ? "无" : String.join("、", siteDomains),
                StringUtils.hasText(contacts) ? contacts : "无",
                Optional.ofNullable(questionText).orElse(""),
                Optional.ofNullable(responseText).orElse("")
        );
    }

    private JudgeInfo parseJudgeInfo(String responseText, String judgeModel) {
        if (!StringUtils.hasText(responseText)) {
            return JudgeInfo.failed("JUDGE_EMPTY", "裁判模型返回为空");
        }
        String jsonText = extractJsonObject(responseText);
        JSONObject payload = JSONUtil.parseObj(jsonText);
        boolean effectiveHit = Boolean.TRUE.equals(payload.getBool("effectiveHit"));
        String hitLevel = normalizeEnum(payload.getStr("hitLevel"), Set.of("strong", "normal", "weak", "invalid"), "invalid");
        String sentiment = normalizeEnum(payload.getStr("sentiment"), Set.of("positive", "neutral", "negative", "unknown"), "unknown");
        String mentionType = normalizeEnum(payload.getStr("mentionType"),
                Set.of("recommendation", "list_inclusion", "factual_mention", "contact_mention", "question_echo", "negative_mention", "irrelevant", "not_mentioned"),
                effectiveHit ? "factual_mention" : "irrelevant");
        boolean contactMentioned = Boolean.TRUE.equals(payload.getBool("contactMentioned"));
        String contactMentionType = normalizeEnum(payload.getStr("contactMentionType"),
                Set.of("phone", "address", "website", "online_service", "store_visit", "generic_contact", "none"),
                contactMentioned ? "generic_contact" : "none");
        return JudgeInfo.success(
                effectiveHit,
                effectiveHit ? hitLevel : "invalid",
                sentiment,
                mentionType,
                contactMentioned,
                contactMentionType,
                trimToLength(payload.getStr("contactEvidence"), 500),
                trimToLength(payload.getStr("evidence"), 500),
                trimToLength(payload.getStr("riskReason"), 500),
                judgeModel
        );
    }

    private String extractJsonObject(String responseText) {
        String raw = responseText.trim();
        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return raw.substring(first, last + 1);
        }
        return raw;
    }

    private String normalizeEnum(String value, Set<String> allowed, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private MatchInfo analyzeMatch(Set<String> projectNames, Set<String> siteDomains, Set<String> phones, Set<String> contactTerms, String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return MatchInfo.empty();
        }
        String raw = responseText.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalizedText = normalizeTextForMatch(raw);
        boolean nameHit = false;
        String matchType = null;
        for (String name : projectNames) {
            String n = name.trim();
            if (!StringUtils.hasText(n)) {
                continue;
            }
            if (raw.contains(n)) {
                nameHit = true;
                matchType = n.equals(projectNames.stream().findFirst().orElse("")) ? "exact" : "alias";
                break;
            }
            String normalizedName = normalizeTextForMatch(n);
            if (StringUtils.hasText(normalizedName) && normalizedText.contains(normalizedName)) {
                nameHit = true;
                matchType = "partial";
                break;
            }
        }
        boolean siteMentioned = siteDomains.stream().anyMatch(lower::contains);
        String normalizedResponseDigits = normalizePhone(raw);
        int phoneMentionCount = phones.stream()
                .filter(StringUtils::hasText)
                .mapToInt(phone -> countOccurrences(normalizedResponseDigits, phone))
                .sum();
        int contactTermMentionCount = contactTerms.stream()
                .filter(StringUtils::hasText)
                .mapToInt(term -> countOccurrences(raw, term))
                .sum();
        int contactMentionCount = phoneMentionCount + contactTermMentionCount;
        return new MatchInfo(nameHit, matchType, siteMentioned, contactMentionCount > 0, contactMentionCount);
    }

    private int countOccurrences(String source, String token) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(token)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private String extractDomain(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        String candidate = rawUrl.trim().toLowerCase(Locale.ROOT);
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizePhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            digits = digits.substring(digits.length() - 11);
        }
        return digits;
    }

    private String normalizeTextForMatch(String raw) {
        return EntityMatchTextNormalizer.normalize(raw);
    }

    private String redactSensitive(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String text = raw;
        text = URL_QUERY_PATTERN.matcher(text).replaceAll("$1");
        text = TOKEN_PATTERN.matcher(text).replaceAll("$1=[REDACTED]");

        Matcher phoneMatcher = PHONE_TEXT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (phoneMatcher.find()) {
            String normalized = normalizePhone(phoneMatcher.group(1));
            String replacement = phoneMatcher.group(1);
            if (normalized.length() >= 7) {
                String prefix = normalized.substring(0, Math.min(3, normalized.length()));
                String suffix = normalized.substring(Math.max(normalized.length() - 4, 0));
                replacement = prefix + "****" + suffix;
            }
            phoneMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        phoneMatcher.appendTail(sb);
        return sb.toString();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> parsePayloadMap(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            JSONUtil.parseObj(payloadJson).forEach((k, v) -> map.put(String.valueOf(k), v));
            return map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean isPendingStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String v = status.trim().toLowerCase(Locale.ROOT);
        return "pending".equals(v) || "draft".equals(v) || "not_started".equals(v) || "paused".equals(v);
    }

    private List<AiPlatformConfig> resolvePlatformCandidates(Long projectId, DispatchTaskType type) {
        if (type == DispatchTaskType.CONTENT_GENERATION) {
            return resolveArticlePlatformCandidates();
        }
        if (type == DispatchTaskType.BRAND_STATEMENT_GENERATION) {
            return resolveRandomEnabledPlatformCandidates();
        }
        if (type == DispatchTaskType.QUESTION_POLL) {
            return resolveQuestionPollPlatformCandidates();
        }

        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
        );
    }

    private List<AiPlatformConfig> resolveQuestionPollPlatformCandidates() {
        List<AiPlatformConfig> candidates = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForQuestionPoll, true)
                        .in(AiPlatformConfig::getUsageScene, "STANDARD_CHAT", "QUESTION_POLL_WEB")
                        .orderByAsc(AiPlatformConfig::getPriorityLevel, AiPlatformConfig::getId)
        );
        return QuestionPollPlatformSelection.preferredEnabled(candidates);
    }

    private List<AiPlatformConfig> resolveRandomEnabledPlatformCandidates() {
        List<AiPlatformConfig> configs = new ArrayList<>(aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getId)
        ));
        Collections.shuffle(configs);
        return configs;
    }

    private List<AiPlatformConfig> resolveArticlePlatformCandidates() {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForArticle, true)
                .orderByAsc(AiPlatformConfig::getId);
        Set<String> excluded = LlmPlatformCodeFilters.parseCodes(articleExcludedPlatformCodes);
        if (!excluded.isEmpty()) {
            wrapper.notIn(AiPlatformConfig::getPlatformCode, excluded);
        }
        List<AiPlatformConfig> configs = aiPlatformConfigMapper.selectList(wrapper);
        if (excluded.isEmpty()) {
            return configs;
        }
        return configs.stream()
                .filter(config -> config != null
                        && !excluded.contains(LlmPlatformCodeFilters.normalize(config.getPlatformCode())))
                .collect(Collectors.toList());
    }

    private InvocationResult invokeWithFallback(AiPlatformConfig config, DispatchTask task, String questionText) {
        Exception lastError = null;
        String lastErrorCode = "UNKNOWN";
        String lastErrorMessage = null;
        int requestCount = 0;

        try {
            requestCount++;
            return invokePrimary(config, task, questionText, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Primary invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        try {
            requestCount++;
            return invokeBackupKey(config, task, questionText, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Backup key invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        try {
            requestCount++;
            return invokeBackupProvider(config, task, questionText, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Backup provider invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        return InvocationResult.failure(lastErrorCode, lastErrorMessage, requestCount, lastError);
    }

    private InvocationResult invokeContentWithFallback(AiPlatformConfig config,
                                                       DispatchTask task,
                                                       String systemPrompt,
                                                       String userPrompt) {
        Exception lastError = null;
        String lastErrorCode = "UNKNOWN";
        String lastErrorMessage = null;
        int requestCount = 0;

        try {
            requestCount++;
            return invokePrimaryContent(config, task, systemPrompt, userPrompt, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Primary content invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        try {
            requestCount++;
            return invokeBackupKeyContent(config, task, systemPrompt, userPrompt, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Backup key content invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        try {
            requestCount++;
            return invokeBackupProviderContent(config, task, systemPrompt, userPrompt, requestCount);
        } catch (Exception ex) {
            lastError = ex;
            lastErrorCode = resolveErrorCode(ex);
            lastErrorMessage = ex.getMessage();
            log.warn("Backup provider content invocation failed for platform {}", config.getPlatformCode(), ex);
        }

        return InvocationResult.failure(lastErrorCode, lastErrorMessage, requestCount, lastError);
    }

    private InvocationResult invokePrimary(AiPlatformConfig config, DispatchTask task, String questionText, int requestCount) {
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing primary api key");
        }
        task.setPlatformCode(config.getPlatformCode());
        task.setCurrentChannel("primary");
        return invokePlatform(config, config.getPlatformCode(), config.getApiUrl(), config.getModelId(), apiKey, task, questionText, "primary", requestCount);
    }

    private InvocationResult invokePrimaryContent(AiPlatformConfig config,
                                                  DispatchTask task,
                                                  String systemPrompt,
                                                  String userPrompt,
                                                  int requestCount) {
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing primary api key");
        }
        task.setPlatformCode(config.getPlatformCode());
        task.setCurrentChannel("primary");
        return invokeContentPlatform(config, config.getPlatformCode(), config.getApiUrl(), config.getModelId(), apiKey, task, systemPrompt, userPrompt, "primary", requestCount);
    }

    private InvocationResult invokeBackupKey(AiPlatformConfig config, DispatchTask task, String questionText, int requestCount) {
        String backupRef = config.getBackupKeyRef();
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), backupRef, null);
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing backup api key");
        }
        task.setPlatformCode(config.getPlatformCode());
        task.setCurrentChannel("backup_key");
        return invokePlatform(config, config.getPlatformCode(), config.getApiUrl(), config.getModelId(), apiKey, task, questionText, "backup_key", requestCount);
    }

    private InvocationResult invokeBackupKeyContent(AiPlatformConfig config,
                                                    DispatchTask task,
                                                    String systemPrompt,
                                                    String userPrompt,
                                                    int requestCount) {
        String backupRef = config.getBackupKeyRef();
        String apiKey = platformCredentialService.resolveApiKey(config.getPlatformCode(), backupRef, null);
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing backup api key");
        }
        task.setPlatformCode(config.getPlatformCode());
        task.setCurrentChannel("backup_key");
        return invokeContentPlatform(config, config.getPlatformCode(), config.getApiUrl(), config.getModelId(), apiKey, task, systemPrompt, userPrompt, "backup_key", requestCount);
    }

    private InvocationResult invokeBackupProvider(AiPlatformConfig config, DispatchTask task, String questionText, int requestCount) {
        if (!StringUtils.hasText(config.getBackupProviderName())) {
            throw new BizException(500, "Missing backup provider");
        }
        AiPlatformConfig backup = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, config.getBackupProviderName().trim())
                        .eq(AiPlatformConfig::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (backup == null) {
            throw new BizException(500, "Backup provider not found");
        }
        String apiUrl = StringUtils.hasText(config.getBackupApiUrl()) ? config.getBackupApiUrl().trim() : backup.getApiUrl();
        String modelId = StringUtils.hasText(config.getBackupModelId()) ? config.getBackupModelId().trim() : backup.getModelId();
        String apiKey = platformCredentialService.resolveApiKey(backup.getPlatformCode(), backup.getPrimaryKeyRef(), backup.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing backup provider api key");
        }
        task.setPlatformCode(backup.getPlatformCode());
        task.setCurrentChannel("backup_provider");
        return invokePlatform(backup, backup.getPlatformCode(), apiUrl, modelId, apiKey, task, questionText, "backup_provider", requestCount);
    }

    private InvocationResult invokeBackupProviderContent(AiPlatformConfig config,
                                                         DispatchTask task,
                                                         String systemPrompt,
                                                         String userPrompt,
                                                         int requestCount) {
        if (!StringUtils.hasText(config.getBackupProviderName())) {
            throw new BizException(500, "Missing backup provider");
        }
        AiPlatformConfig backup = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, config.getBackupProviderName().trim())
                        .eq(AiPlatformConfig::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (backup == null) {
            throw new BizException(500, "Backup provider not found");
        }
        String apiUrl = StringUtils.hasText(config.getBackupApiUrl()) ? config.getBackupApiUrl().trim() : backup.getApiUrl();
        String modelId = StringUtils.hasText(config.getBackupModelId()) ? config.getBackupModelId().trim() : backup.getModelId();
        String apiKey = platformCredentialService.resolveApiKey(backup.getPlatformCode(), backup.getPrimaryKeyRef(), backup.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Missing backup provider api key");
        }
        task.setPlatformCode(backup.getPlatformCode());
        task.setCurrentChannel("backup_provider");
        return invokeContentPlatform(backup, backup.getPlatformCode(), apiUrl, modelId, apiKey, task, systemPrompt, userPrompt, "backup_provider", requestCount);
    }

    private InvocationResult invokePlatform(AiPlatformConfig config,
                                            String platformCode,
                                            String apiUrl,
                                            String modelId,
                                            String apiKey,
                                            DispatchTask task,
                                            String questionText,
                                            String channel,
                                            int requestCount) {
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(modelId) || !StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Invalid platform invocation params");
        }
        long started = System.currentTimeMillis();
        String prompt = buildPrompt(task, questionText);
        String response = invokeModelApi(config, platformCode, channel, apiUrl, modelId, apiKey, prompt, requestCount);
        long durationMs = Math.max(1L, System.currentTimeMillis() - started);
        log.info("Dispatch task {} executed by platform={}, model={}, channel={}, questionPresent={}",
                task.getId(), platformCode, modelId, channel, StringUtils.hasText(questionText));
        return InvocationResult.success(platformCode, channel, response, Math.max(durationMs, 1L), requestCount);
    }

    private InvocationResult invokeContentPlatform(AiPlatformConfig config,
                                                   String platformCode,
                                                   String apiUrl,
                                                   String modelId,
                                                   String apiKey,
                                                   DispatchTask task,
                                                   String systemPrompt,
                                                   String userPrompt,
                                                   String channel,
                                                   int requestCount) {
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(modelId) || !StringUtils.hasText(apiKey)) {
            throw new BizException(500, "Invalid platform invocation params");
        }
        long started = System.currentTimeMillis();
        String response = invokeModelApi(config, platformCode, channel, apiUrl, modelId, apiKey,
                systemPrompt, userPrompt, requestCount);
        long durationMs = Math.max(1L, System.currentTimeMillis() - started);
        log.info("Content generation task {} executed by platform={}, model={}, channel={}",
                task.getId(), platformCode, modelId, channel);
        return InvocationResult.success(platformCode, channel, response, Math.max(durationMs, 1L), requestCount);
    }

    private String buildPrompt(DispatchTask task, String questionText) {
        if (StringUtils.hasText(questionText)) {
            return questionText;
        }
        return "Please execute task " + task.getTaskType() + " for project " + task.getProjectId();
    }

    private String invokeModelApi(AiPlatformConfig config,
                                  String platformCode,
                                  String channel,
                                  String apiUrl,
                                  String modelId,
                                  String apiKey,
                                  String prompt,
                                  int requestCount) {
        return invokeModelApi(config, platformCode, channel, apiUrl, modelId, apiKey,
                "You are a GEO (Generative Engine Optimization) monitoring assistant.", prompt, 0D, requestCount);
    }

    private String invokeModelApi(AiPlatformConfig config,
                                  String platformCode,
                                  String channel,
                                  String apiUrl,
                                  String modelId,
                                  String apiKey,
                                  String systemPrompt,
                                  String userPrompt,
                                  int requestCount) {
        return invokeModelApi(config, platformCode, channel, apiUrl, modelId, apiKey,
                systemPrompt, userPrompt, 0.7D, requestCount);
    }

    private String invokeModelApi(AiPlatformConfig config,
                                  String platformCode,
                                  String channel,
                                  String apiUrl,
                                  String modelId,
                                  String apiKey,
                                  String systemPrompt,
                                  String userPrompt,
                                  double temperature,
                                  int requestCount) {
        try {
            LlmCallResult result = llmCallFacade.execute(LlmCallRequest.legacy(
                    config,
                    platformCode,
                    config.getPlatformName(),
                    channel,
                    apiUrl,
                    modelId,
                    apiKey,
                    systemPrompt,
                    userPrompt,
                    temperature,
                    1000,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    dispatchProperties.getModelRequestTimeoutMs(),
                    LlmFeature.GENERIC,
                    requestCount
            ));
            String body = result.rawResponseText();
            String text = extractResponseText(body);
            if (!StringUtils.hasText(text)) {
                throw new BizException(502, "Model API empty response text");
            }
            return text;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(500, "Model API invoke failed: " + ex.getMessage());
        }
    }

    private String extractResponseText(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            Object parsed = JSONUtil.parse(body);
            if (!(parsed instanceof cn.hutool.json.JSONObject json)) {
                return body;
            }
            if (json.containsKey("choices")) {
                Object choicesObj = json.get("choices");
                if (choicesObj instanceof cn.hutool.json.JSONArray arr && !arr.isEmpty()) {
                    Object first = arr.get(0);
                    if (first instanceof cn.hutool.json.JSONObject firstObj) {
                        Object messageObj = firstObj.get("message");
                        if (messageObj instanceof cn.hutool.json.JSONObject messageJson) {
                            String content = messageJson.getStr("content");
                            if (StringUtils.hasText(content)) {
                                return content;
                            }
                        }
                        String text = firstObj.getStr("text");
                        if (StringUtils.hasText(text)) {
                            return text;
                        }
                    }
                }
            }
            if (json.containsKey("output_text")) {
                String outputText = json.getStr("output_text");
                if (StringUtils.hasText(outputText)) {
                    return outputText;
                }
            }
            return body;
        } catch (Exception ex) {
            return body;
        }
    }

    private String safeSnippet(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private String resolveErrorCode(Exception ex) {
        if (ex instanceof BizException biz) {
            return "BIZ_" + biz.getCode();
        }
        return "SYS_500";
    }

    private static class InvocationResult {
        private final boolean success;
        private final String platformCode;
        private final String channel;
        private final String responseText;
        private final long responseTimeMs;
        private final int requestCount;
        private final String errorCode;
        private final String errorMessage;
        private final Exception error;

        private InvocationResult(boolean success,
                                 String platformCode,
                                 String channel,
                                 String responseText,
                                 long responseTimeMs,
                                 int requestCount,
                                 String errorCode,
                                 String errorMessage,
                                 Exception error) {
            this.success = success;
            this.platformCode = platformCode;
            this.channel = channel;
            this.responseText = responseText;
            this.responseTimeMs = responseTimeMs;
            this.requestCount = requestCount;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.error = error;
        }

        static InvocationResult success(String platformCode, String channel, String responseText, long responseTimeMs, int requestCount) {
            return new InvocationResult(true, platformCode, channel, responseText, responseTimeMs, requestCount, null, null, null);
        }

        static InvocationResult failure(String errorCode, String errorMessage, int requestCount, Exception error) {
            return new InvocationResult(false, null, null, null, 0L, requestCount, errorCode, errorMessage, error);
        }
    }

    private static class JudgeInfo {
        private final Boolean effectiveHit;
        private final String status;
        private final String hitLevel;
        private final String sentiment;
        private final String mentionType;
        private final Boolean contactMentioned;
        private final String contactMentionType;
        private final String contactEvidence;
        private final String evidence;
        private final String riskReason;
        private final String judgeModel;
        private final LocalDateTime judgeAt;
        private final String error;

        private JudgeInfo(Boolean effectiveHit,
                          String status,
                          String hitLevel,
                          String sentiment,
                          String mentionType,
                          Boolean contactMentioned,
                          String contactMentionType,
                          String contactEvidence,
                          String evidence,
                          String riskReason,
                          String judgeModel,
                          LocalDateTime judgeAt,
                          String error) {
            this.effectiveHit = effectiveHit;
            this.status = status;
            this.hitLevel = hitLevel;
            this.sentiment = sentiment;
            this.mentionType = mentionType;
            this.contactMentioned = contactMentioned;
            this.contactMentionType = contactMentionType;
            this.contactEvidence = contactEvidence;
            this.evidence = evidence;
            this.riskReason = riskReason;
            this.judgeModel = judgeModel;
            this.judgeAt = judgeAt;
            this.error = error;
        }

        static JudgeInfo success(boolean effectiveHit,
                                 String hitLevel,
                                 String sentiment,
                                 String mentionType,
                                 boolean contactMentioned,
                                 String contactMentionType,
                                 String contactEvidence,
                                 String evidence,
                                 String riskReason,
                                 String judgeModel) {
            return new JudgeInfo(effectiveHit, "success", hitLevel, sentiment, mentionType,
                    contactMentioned, contactMentionType, contactEvidence, evidence, riskReason, judgeModel, LocalDateTime.now(), null);
        }

        static JudgeInfo skipped(String reason) {
            return new JudgeInfo(null, "skipped", null, null, null, null, null, "", "", "", null, null, reason);
        }

        static JudgeInfo failed(String code, String message) {
            String error = StringUtils.hasText(message) ? code + ": " + message : code;
            if (StringUtils.hasText(error) && error.length() > 500) {
                error = error.substring(0, 500);
            }
            return new JudgeInfo(null, "failed", null, null, null, null, null, "", "", "", null, LocalDateTime.now(), error);
        }

        Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("effective_hit", effectiveHit);
            payload.put("judge_status", status);
            payload.put("hit_level", hitLevel);
            payload.put("sentiment", sentiment);
            payload.put("mention_type", mentionType);
            payload.put("contact_mentioned", contactMentioned);
            payload.put("contact_mention_type", contactMentionType);
            payload.put("contact_evidence", contactEvidence);
            payload.put("evidence", evidence);
            payload.put("risk_reason", riskReason);
            payload.put("judge_model", judgeModel);
            payload.put("judge_at", judgeAt);
            payload.put("judge_error", error);
            return payload;
        }
    }

    private static class MatchInfo {
        private final boolean hit;
        private final String matchType;
        private final boolean siteMentioned;
        private final boolean contactMentioned;
        private final int contactMentionCount;

        private MatchInfo(boolean hit, String matchType, boolean siteMentioned, boolean contactMentioned, int contactMentionCount) {
            this.hit = hit;
            this.matchType = matchType;
            this.siteMentioned = siteMentioned;
            this.contactMentioned = contactMentioned;
            this.contactMentionCount = contactMentionCount;
        }

        static MatchInfo empty() {
            return new MatchInfo(false, null, false, false, 0);
        }
    }

    private record PollKeywordCandidate(Long keywordResultId, String keywordText) {
    }
}
