package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.common.util.HttpClientUtil;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.entity.ArticleBatch;
import com.huanjing.geo.module.content.entity.ArticleGenerationLog;
import com.huanjing.geo.module.content.entity.PackageContentConfig;
import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.PackageContentConfigMapper;
import com.huanjing.geo.module.content.service.ContentArticleService;
import com.huanjing.geo.module.content.service.GeoPromptBuilder;
import com.huanjing.geo.module.content.service.ArticleGenerationPersistenceService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.entity.ProjectPollRotation;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.entity.ProjectPlatformBinding;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectPlatformBindingMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private static final Set<String> QUESTION_TIERS = Set.of("A", "B", "C");

    private final ProjectPlatformBindingMapper projectPlatformBindingMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PlatformCredentialService platformCredentialService;
    private final PlatformRateLimiterService platformRateLimiterService;
    private final PlatformConcurrencyLimiterService platformConcurrencyLimiterService;
    private final LlmPlatformRouter llmPlatformRouter;
    private final ProjectMapper projectMapper;
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final PackageContentConfigMapper packageContentConfigMapper;
    private final ArticleBatchMapper articleBatchMapper;
    private final ArticleGenerationLogMapper articleGenerationLogMapper;
    private final ContentArticleService contentArticleService;
    private final GeoPromptBuilder geoPromptBuilder;
    private final ArticleGenerationPersistenceService articleGenerationPersistenceService;
    private final ArticleGenerationWindowLockService articleGenerationWindowLockService;
    private final PollBatchMapper pollBatchMapper;
    private final PollResultMapper pollResultMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final ProjectPollRotationMapper projectPollRotationMapper;
    private final CompanyMapper companyMapper;
    private final BrandMapper brandMapper;
    private final CompanyPackageBindingService companyPackageBindingService;
    private final BrandStatementService brandStatementService;
    private final SysDictItemMapper sysDictItemMapper;
    private final DispatchProperties dispatchProperties;

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
        if (type == DispatchTaskType.BI_DAILY_POLL) {
            executeBiDailyPoll(task, platformConfigs);
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

    private void executeBiDailyPoll(DispatchTask task, List<AiPlatformConfig> platformConfigs) {
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        String questionTier = resolveQuestionTier(task);
        List<PollKeywordCandidate> allKeywords = loadProjectPollKeywords(project.getId(), questionTier);
        if (allKeywords.isEmpty()) {
            log.info("Skip BI_DAILY_POLL task {} because no {} tier saved keywords for project {}",
                    task.getId(), questionTier, task.getProjectId());
            return;
        }

        int planCap = resolveTierPollLimit(project, questionTier);
        int takeCount = planCap > 0 ? Math.min(planCap, allKeywords.size()) : allKeywords.size();
        List<PollKeywordCandidate> selected = selectRotatedKeywords(project.getId(), questionTier, allKeywords, takeCount);

        LocalDate batchDate = resolveBatchDate(task);
        int batchNo = resolveBatchNo(task);
        PollBatch batch = ensureBatch(task, project, batchDate, batchNo, questionTier, selected.size(), platformConfigs.size());

        Set<String> projectNames = resolveProjectNameSet(project);
        Set<String> siteDomains = resolveSiteDomains(project);
        Set<String> normalizedPhones = resolvePhones(project);
        Set<String> contactTerms = resolveContactTerms(project);

        Map<Long, PlatformAgg> aggByPlatform = new LinkedHashMap<>();
        for (AiPlatformConfig platform : platformConfigs) {
            aggByPlatform.put(platform.getId(), new PlatformAgg(platform));
        }

        for (AiPlatformConfig platform : platformConfigs) {
            PlatformAgg agg = aggByPlatform.get(platform.getId());
            for (PollKeywordCandidate keyword : selected) {
                InvocationResult invokeResult = invokeMonitoringWithRouter(platform, task, keyword.keywordText());
                PollResult detail = buildPollResult(batch, task, project, platform, keyword, invokeResult, projectNames, siteDomains, normalizedPhones, contactTerms);
                upsertPollResult(detail);
                agg.questionCount += 1;
                agg.requestCount += Math.max(detail.getRequestCount() == null ? 0 : detail.getRequestCount(), 0);
                if ("completed".equals(detail.getStatus())) {
                    agg.completedCount += 1;
                    if (Boolean.TRUE.equals(detail.getIsHit())) {
                        agg.hitCount += 1;
                    }
                    if (Boolean.TRUE.equals(detail.getSiteMentioned())) {
                        agg.siteMentionCount += 1;
                    }
                    if (Boolean.TRUE.equals(detail.getContactMentioned())) {
                        agg.contactMentionCount += 1;
                    }
                } else {
                    agg.failedCount += 1;
                }
            }
        }

        int totalQuestionCount = aggByPlatform.values().stream().mapToInt(a -> a.questionCount).sum();
        int totalCompleted = aggByPlatform.values().stream().mapToInt(a -> a.completedCount).sum();
        int totalFailed = aggByPlatform.values().stream().mapToInt(a -> a.failedCount).sum();
        int totalHit = aggByPlatform.values().stream().mapToInt(a -> a.hitCount).sum();
        BigDecimal overallHitRate = totalCompleted <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalHit).divide(BigDecimal.valueOf(totalCompleted), 4, RoundingMode.HALF_UP);

        for (PlatformAgg agg : aggByPlatform.values()) {
            PollDailyStat stat = new PollDailyStat();
            stat.setBatchId(batch.getId());
            stat.setDispatchTaskId(task.getId());
            stat.setProjectId(project.getId());
            stat.setProjectName(project.getProjectName());
            stat.setPlatformId(agg.platform.getId());
            stat.setPlatformCode(agg.platform.getPlatformCode());
            stat.setPlatformName(agg.platform.getPlatformName());
            stat.setBatchDate(batchDate);
            stat.setBatchNo(batchNo);
            stat.setQuestionTier(questionTier);
            stat.setQuestionCount(agg.questionCount);
            stat.setRequestCount(agg.requestCount);
            stat.setCompletedCount(agg.completedCount);
            stat.setFailedCount(agg.failedCount);
            stat.setHitCount(agg.hitCount);
            stat.setSiteMentionCount(agg.siteMentionCount);
            stat.setContactMentionCount(agg.contactMentionCount);
            stat.setHitRate(agg.completedCount <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(agg.hitCount).divide(BigDecimal.valueOf(agg.completedCount), 4, RoundingMode.HALF_UP));
            upsertPollStat(stat);
        }

        batch.setQuestionCount(totalQuestionCount);
        batch.setCompletedCount(totalCompleted);
        batch.setFailedCount(totalFailed);
        batch.setHitCount(totalHit);
        batch.setOverallHitRate(overallHitRate);
        batch.setFinishedAt(LocalDateTime.now());
        pollBatchMapper.updateById(batch);
    }

    private PollResult buildPollResult(PollBatch batch,
                                       DispatchTask task,
                                       Project project,
                                       AiPlatformConfig platform,
                                       PollKeywordCandidate keyword,
                                       InvocationResult invokeResult,
                                       Set<String> projectNames,
                                       Set<String> siteDomains,
                                       Set<String> normalizedPhones,
                                       Set<String> contactTerms) {
        MatchInfo match = invokeResult.success
                ? analyzeMatch(projectNames, siteDomains, normalizedPhones, contactTerms, invokeResult.responseText)
                : MatchInfo.empty();

        String recordType;
        if (!invokeResult.success) {
            recordType = "error";
        } else if (match.hit) {
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
            matchDetails.put("is_hit", match.hit);
            matchDetails.put("match_type", match.matchType);
            matchDetails.put("site_mentioned", match.siteMentioned);
            matchDetails.put("contact_mentioned", match.contactMentioned);
            detail.put("match_details", matchDetails);
        } else {
            Map<String, Object> errorPayload = new LinkedHashMap<>();
            errorPayload.put("error_code", invokeResult.errorCode);
            errorPayload.put("error_message", redactSensitive(invokeResult.errorMessage));
            errorPayload.put("raw_response_snippet", redactSensitive(invokeResult.responseText));
            detail.put("error_payload", errorPayload);
        }

        PollResult result = new PollResult();
        result.setBatchId(batch.getId());
        result.setDispatchTaskId(task.getId());
        result.setProjectId(project.getId());
        result.setKeywordResultId(keyword.keywordResultId());
        result.setKeywordTextSnapshot(keyword.keywordText());
        result.setPlatformId(platform.getId());
        result.setPlatformCode(platform.getPlatformCode());
        result.setBatchDate(batch.getBatchDate());
        result.setBatchNo(batch.getBatchNo());
        result.setQuestionTier(batch.getQuestionTier());
        result.setStatus(invokeResult.success ? "completed" : "failed");
        result.setRequestCount(invokeResult.requestCount);
        result.setResponseTimeMs(invokeResult.responseTimeMs);
        result.setIsHit(match.hit);
        result.setMatchType(match.matchType);
        result.setSiteMentioned(match.siteMentioned);
        result.setContactMentioned(match.contactMentioned);
        result.setRecordType(recordType);
        result.setDetailJson(JSONUtil.toJsonStr(detail));
        return result;
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
            LlmRouteResult routed = llmPlatformRouter.invoke(new LlmRouteRequest(
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
            ));
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
            if (isCapacityFailure(ex.failureKind())) {
                throw new DispatchResourceBusyException(ex.getMessage(), ex);
            }
            return InvocationResult.failure(ex.failureKind().name(), ex.getMessage(), ex.requestCount(), ex);
        }
    }

    private InvocationResult invokeMonitoringWithRouter(AiPlatformConfig platform,
                                                        DispatchTask task,
                                                        String questionText) {
        try {
            LlmRouteResult routed = llmPlatformRouter.invoke(new LlmRouteRequest(
                    LlmFeature.MONITORING,
                    "You are a GEO monitoring assistant.",
                    questionText,
                    0D,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    dispatchProperties.getModelRequestTimeoutMs(),
                    LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                    null,
                    null,
                    false,
                    1000,
                    0,
                    List.of(platform)
            ));
            task.setPlatformCode(routed.platformCode());
            task.setCurrentChannel(routed.channel());
            log.info("BI_DAILY_POLL task {} executed by platform={}, model={}, channel={}",
                    task.getId(), routed.platformCode(), routed.modelId(), routed.channel());
            return InvocationResult.success(
                    routed.platformCode(),
                    routed.channel(),
                    routed.responseText(),
                    Math.max(routed.durationMs(), 1L),
                    routed.requestCount()
            );
        } catch (LlmRouteException ex) {
            if (isCapacityFailure(ex.failureKind())) {
                throw new DispatchResourceBusyException(ex.getMessage(), ex);
            }
            return InvocationResult.failure(ex.failureKind().name(), ex.getMessage(), ex.requestCount(), ex);
        }
    }

    private boolean isCapacityFailure(LlmRouteFailureKind failureKind) {
        return failureKind == LlmRouteFailureKind.ALL_RATE_LIMITED
                || failureKind == LlmRouteFailureKind.ALL_PERMIT_BUSY
                || failureKind == LlmRouteFailureKind.ALL_CIRCUIT_OPEN;
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
        return "You are a GEO content strategist. Generate a structured brand statement JSON with keys: "
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
        if (words.isEmpty()) {
            return "无";
        }
        return String.join("、", words);
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

    private void upsertPollStat(PollDailyStat stat) {
        withTransientDbRetry("upsert poll stat", () -> {
            PollDailyStat existing = pollDailyStatMapper.selectOne(
                    new LambdaQueryWrapper<PollDailyStat>()
                            .eq(PollDailyStat::getProjectId, stat.getProjectId())
                            .eq(PollDailyStat::getPlatformId, stat.getPlatformId())
                            .eq(PollDailyStat::getBatchDate, stat.getBatchDate())
                            .eq(PollDailyStat::getBatchNo, stat.getBatchNo())
                            .eq(PollDailyStat::getQuestionTier, stat.getQuestionTier())
                            .last("LIMIT 1")
            );
            if (existing == null) {
                pollDailyStatMapper.insert(stat);
                return null;
            }
            stat.setId(existing.getId());
            pollDailyStatMapper.updateById(stat);
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

    private PollBatch ensureBatch(DispatchTask task,
                                  Project project,
                                  LocalDate batchDate,
                                  int batchNo,
                                  String questionTier,
                                  int totalQuestions,
                                  int totalPlatforms) {
        PollBatch existing = pollBatchMapper.selectOne(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getProjectId, project.getId())
                        .eq(PollBatch::getBatchDate, batchDate)
                        .eq(PollBatch::getBatchNo, batchNo)
                        .eq(PollBatch::getQuestionTier, questionTier)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            existing.setDispatchTaskId(task.getId());
            existing.setTriggeredAt(LocalDateTime.now());
            existing.setFinishedAt(null);
            existing.setTotalQuestionCount(totalQuestions);
            existing.setTotalPlatformCount(totalPlatforms);
            existing.setQuestionCount(0);
            existing.setCompletedCount(0);
            existing.setFailedCount(0);
            existing.setHitCount(0);
            existing.setOverallHitRate(BigDecimal.ZERO);
            pollBatchMapper.updateById(existing);
            return existing;
        }
        PollBatch batch = new PollBatch();
        batch.setDispatchTaskId(task.getId());
        batch.setProjectId(project.getId());
        batch.setBatchDate(batchDate);
        batch.setBatchNo(batchNo);
        batch.setQuestionTier(questionTier);
        batch.setTriggeredAt(LocalDateTime.now());
        batch.setTotalQuestionCount(totalQuestions);
        batch.setTotalPlatformCount(totalPlatforms);
        batch.setQuestionCount(0);
        batch.setCompletedCount(0);
        batch.setFailedCount(0);
        batch.setHitCount(0);
        batch.setOverallHitRate(BigDecimal.ZERO);
        pollBatchMapper.insert(batch);
        return batch;
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
            throw new BizException(400, "Invalid question tier for BI_DAILY_POLL: " + value);
        }
        return tier;
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

    private List<PollKeywordCandidate> loadProjectPollKeywords(Long projectId, String questionTier) {
        List<Long> groupIds = projectKeywordGroupRelMapper.selectList(
                new LambdaQueryWrapper<ProjectKeywordGroupRel>()
                        .eq(ProjectKeywordGroupRel::getProjectId, projectId)
                        .select(ProjectKeywordGroupRel::getKeywordGroupId)
        ).stream().map(ProjectKeywordGroupRel::getKeywordGroupId).filter(Objects::nonNull).distinct().toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }

        List<KeywordGroupResult> results = keywordGroupResultMapper.selectList(
                new LambdaQueryWrapper<KeywordGroupResult>()
                        .in(KeywordGroupResult::getGroupId, groupIds)
                        .eq(KeywordGroupResult::getQuestionTier, questionTier)
                        .orderByAsc(KeywordGroupResult::getId)
        );
        if (results.isEmpty()) {
            return List.of();
        }

        Map<String, PollKeywordCandidate> deduplicated = new LinkedHashMap<>();
        for (KeywordGroupResult result : results) {
            String keywordText = normalizeKeywordText(result.getKeywordText());
            if (!StringUtils.hasText(keywordText)) {
                continue;
            }
            deduplicated.putIfAbsent(normalizeKeywordKey(keywordText), new PollKeywordCandidate(result.getId(), keywordText));
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<PollKeywordCandidate> selectRotatedKeywords(Long projectId,
                                                             String layer,
                                                             List<PollKeywordCandidate> source,
                                                             int takeCount) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        ProjectPollRotation rotation = ensureRotation(projectId, layer);
        int size = source.size();
        int offset = rotation.getRotationOffset() == null ? 0 : rotation.getRotationOffset();
        int normalizedOffset = Math.floorMod(offset, size);
        int normalizedTakeCount = Math.max(1, Math.min(takeCount, size));

        List<PollKeywordCandidate> picked = new ArrayList<>();
        for (int i = 0; i < normalizedTakeCount; i++) {
            picked.add(source.get((normalizedOffset + i) % size));
        }
        rotation.setRotationOffset((normalizedOffset + normalizedTakeCount) % size);
        projectPollRotationMapper.updateById(rotation);
        return picked;
    }

    private String normalizeKeywordText(String keywordText) {
        return keywordText == null ? null : keywordText.trim();
    }

    private String normalizeKeywordKey(String keywordText) {
        return normalizeKeywordText(keywordText).toLowerCase(Locale.ROOT);
    }

    private ProjectPollRotation ensureRotation(Long projectId, String layer) {
        ProjectPollRotation rotation = projectPollRotationMapper.selectOne(
                new LambdaQueryWrapper<ProjectPollRotation>()
                        .eq(ProjectPollRotation::getProjectId, projectId)
                        .eq(ProjectPollRotation::getPriorityLevel, layer)
                        .last("LIMIT 1")
        );
        if (rotation != null) {
            return rotation;
        }
        ProjectPollRotation created = new ProjectPollRotation();
        created.setProjectId(projectId);
        created.setPriorityLevel(layer);
        created.setRotationOffset(0);
        projectPollRotationMapper.insert(created);
        return created;
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

    private MatchInfo analyzeMatch(Set<String> projectNames, Set<String> siteDomains, Set<String> phones, Set<String> contactTerms, String responseText) {
        if (!StringUtils.hasText(responseText)) {
            return MatchInfo.empty();
        }
        String raw = responseText.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String normalizedText = raw.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase(Locale.ROOT);
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
            String normalizedName = n.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(normalizedName) && normalizedText.contains(normalizedName)) {
                nameHit = true;
                matchType = "partial";
                break;
            }
        }
        boolean siteMentioned = siteDomains.stream().anyMatch(lower::contains);
        String normalizedResponseDigits = normalizePhone(raw);
        boolean phoneMentioned = phones.stream().anyMatch(p -> StringUtils.hasText(p) && normalizedResponseDigits.contains(p));
        boolean contactTermMentioned = contactTerms.stream().anyMatch(term -> StringUtils.hasText(term) && raw.contains(term));
        boolean contactMentioned = phoneMentioned || contactTermMentioned;
        return new MatchInfo(nameHit, matchType, siteMentioned, contactMentioned);
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
            return resolveBrandStatementPlatformCandidates(projectId, type);
        }

        List<ProjectPlatformBinding> bindings = projectPlatformBindingMapper.selectList(
                new LambdaQueryWrapper<ProjectPlatformBinding>()
                        .eq(ProjectPlatformBinding::getProjectId, projectId)
        );
        if (bindings.isEmpty()) {
            return List.of();
        }

        Map<String, String> levelByCode = bindings.stream()
                .collect(Collectors.toMap(ProjectPlatformBinding::getPlatformCode, ProjectPlatformBinding::getPriorityLevel, (a, b) -> a));
        List<AiPlatformConfig> configs = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, levelByCode.keySet())
                        .eq(AiPlatformConfig::getEnabled, true)
        );

        List<String> preferredLevels = preferredLevels(type);
        return configs.stream()
                .filter(cfg -> preferredLevels.contains(levelByCode.get(cfg.getPlatformCode())))
                .sorted(Comparator.comparingInt(cfg -> preferredLevels.indexOf(levelByCode.get(cfg.getPlatformCode()))))
                .collect(Collectors.toList());
    }

    private List<AiPlatformConfig> resolveBrandStatementPlatformCandidates(Long projectId, DispatchTaskType type) {
        List<AiPlatformConfig> boundConfigs = resolveBoundPlatformCandidates(projectId, type);
        if (!boundConfigs.isEmpty()) {
            return boundConfigs;
        }

        List<String> preferredLevels = preferredLevels(type);
        return aiPlatformConfigMapper.selectList(
                        new LambdaQueryWrapper<AiPlatformConfig>()
                                .eq(AiPlatformConfig::getEnabled, true)
                                .eq(AiPlatformConfig::getEnabledForPresale, true)
                ).stream()
                .filter(cfg -> preferredLevels.contains(cfg.getPriorityLevel()))
                .sorted(Comparator
                        .comparingInt((AiPlatformConfig cfg) -> preferredLevels.indexOf(cfg.getPriorityLevel()))
                        .thenComparing(AiPlatformConfig::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    private List<AiPlatformConfig> resolveBoundPlatformCandidates(Long projectId, DispatchTaskType type) {
        if (projectId == null) {
            return List.of();
        }
        List<ProjectPlatformBinding> bindings = projectPlatformBindingMapper.selectList(
                new LambdaQueryWrapper<ProjectPlatformBinding>()
                        .eq(ProjectPlatformBinding::getProjectId, projectId)
        );
        if (bindings.isEmpty()) {
            return List.of();
        }

        Map<String, String> levelByCode = bindings.stream()
                .collect(Collectors.toMap(ProjectPlatformBinding::getPlatformCode, ProjectPlatformBinding::getPriorityLevel, (a, b) -> a));
        List<AiPlatformConfig> configs = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .in(AiPlatformConfig::getPlatformCode, levelByCode.keySet())
                        .eq(AiPlatformConfig::getEnabled, true)
        );

        List<String> preferredLevels = preferredLevels(type);
        return configs.stream()
                .filter(cfg -> preferredLevels.contains(levelByCode.get(cfg.getPlatformCode())))
                .sorted(Comparator.comparingInt(cfg -> preferredLevels.indexOf(levelByCode.get(cfg.getPlatformCode()))))
                .collect(Collectors.toList());
    }

    private List<AiPlatformConfig> resolveArticlePlatformCandidates() {
        return aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .eq(AiPlatformConfig::getEnabledForArticle, true)
                        .orderByAsc(AiPlatformConfig::getId)
        );
    }

    private List<String> preferredLevels(DispatchTaskType type) {
        if (type == DispatchTaskType.QUARTERLY_REPORT) {
            return List.of("P0", "P1", "P2");
        }
        if (type == DispatchTaskType.MONTHLY_REPORT
                || type == DispatchTaskType.BRAND_STATEMENT_GENERATION) {
            return List.of("P1", "P0", "P2");
        }
        return List.of("P0", "P1", "P2");
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
        boolean pass = platformRateLimiterService.tryAcquire(config, 1000);
        if (!pass) {
            throw new BizException(429, "Platform limited: " + platformCode);
        }

        long started = System.currentTimeMillis();
        String response;
        try (PlatformConcurrencyLimiterService.Permit ignored = platformConcurrencyLimiterService.acquire(config)) {
            String prompt = buildPrompt(task, questionText);
            response = invokeModelApi(apiUrl, modelId, apiKey, prompt);
        }
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
        boolean pass = platformRateLimiterService.tryAcquire(config, 1000);
        if (!pass) {
            throw new BizException(429, "Platform limited: " + platformCode);
        }

        long started = System.currentTimeMillis();
        String response;
        try (PlatformConcurrencyLimiterService.Permit ignored = platformConcurrencyLimiterService.acquire(config)) {
            response = invokeModelApi(apiUrl, modelId, apiKey, systemPrompt, userPrompt);
        }
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

    private String invokeModelApi(String apiUrl, String modelId, String apiKey, String prompt) {
        return invokeModelApi(apiUrl, modelId, apiKey, "You are a GEO monitoring assistant.", prompt, 0D);
    }

    private String invokeModelApi(String apiUrl, String modelId, String apiKey, String systemPrompt, String userPrompt) {
        return invokeModelApi(apiUrl, modelId, apiKey, systemPrompt, userPrompt, 0.7D);
    }

    private String invokeModelApi(String apiUrl,
                                  String modelId,
                                  String apiKey,
                                  String systemPrompt,
                                  String userPrompt,
                                  double temperature) {
        String targetUrl = apiUrl.trim();
        if (!targetUrl.endsWith("/chat/completions")) {
            targetUrl = targetUrl.endsWith("/") ? targetUrl + "chat/completions" : targetUrl + "/chat/completions";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelId);
        payload.put("temperature", temperature);
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        payload.put("messages", messages);

        String requestJson = JSONUtil.toJsonStr(payload);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("api-key", apiKey);
        headers.put("x-api-key", apiKey);

        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.postJson(
                    targetUrl,
                    headers,
                    requestJson,
                    dispatchProperties.getModelConnectTimeoutMs(),
                    dispatchProperties.getModelRequestTimeoutMs()
            );
            int code = response.statusCode();
            String body = response.body();
            if (code < 200 || code >= 300) {
                throw new BizException(code, "Model API HTTP " + code + ": " + safeSnippet(body));
            }
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

    private static class MatchInfo {
        private final boolean hit;
        private final String matchType;
        private final boolean siteMentioned;
        private final boolean contactMentioned;

        private MatchInfo(boolean hit, String matchType, boolean siteMentioned, boolean contactMentioned) {
            this.hit = hit;
            this.matchType = matchType;
            this.siteMentioned = siteMentioned;
            this.contactMentioned = contactMentioned;
        }

        static MatchInfo empty() {
            return new MatchInfo(false, null, false, false);
        }
    }

    private static class PlatformAgg {
        private final AiPlatformConfig platform;
        private int questionCount;
        private int requestCount;
        private int completedCount;
        private int failedCount;
        private int hitCount;
        private int siteMentionCount;
        private int contactMentionCount;

        private PlatformAgg(AiPlatformConfig platform) {
            this.platform = platform;
        }
    }

    private record PollKeywordCandidate(Long keywordResultId, String keywordText) {
    }
}
