package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaScheduleConfigRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchDetailVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleConfigVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleRejectedItemVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSelfMediaScheduleService {
    private static final int ERROR_CODE = 70043;
    private static final int GENERATION_BATCH_LIMIT = 30;
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_JOB = "job";

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final ProjectSelfMediaScheduleConfigMapper configMapper;
    private final ProjectSelfMediaScheduleBatchMapper batchMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final SelfMediaPublishScheduleMapper selfMediaPublishScheduleMapper;
    private final SelfMediaPublishScheduleRequestMapper selfMediaPublishScheduleRequestMapper;
    private final BatchArticleGenerationTaskMapper generationTaskMapper;
    private final KeywordGroupResultMapper keywordGroupResultMapper;
    private final SelfMediaPublishAutoScheduleService autoScheduleService;
    private final SelfMediaPublishScheduleService scheduleService;
    private final BatchArticleGenerationService generationService;
    private final TemplatePerspectiveService perspectiveService;
    private final ArticleTemplateAllocationService templateAllocationService;
    private final ArticleCoverSelectionService coverSelectionService;
    private final BusinessCalendarService businessCalendarService;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;

    public ProjectSelfMediaScheduleConfigVO getConfig(Long projectId) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig row = configMapper.selectByProjectId(projectId);
        if (row == null) {
            row = defaultConfig(project);
        }
        return ProjectSelfMediaScheduleConfigVO.from(row);
    }

    @Transactional
    public ProjectSelfMediaScheduleConfigVO updateConfig(Long projectId, ProjectSelfMediaScheduleConfigRequest request) {
        Project project = requireProject(projectId);
        SysUser operator = requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig row = configMapper.selectByProjectId(projectId);
        boolean create = row == null;
        if (create) {
            row = defaultConfig(project);
            row.setCreatedBy(operator.getId());
        }
        if (request != null) {
            if (request.getAutoScheduleEnabled() != null) {
                row.setAutoScheduleEnabled(request.getAutoScheduleEnabled());
            }
            if (StringUtils.hasText(request.getDefaultScheduleStrategy())) {
                row.setDefaultScheduleStrategy(request.getDefaultScheduleStrategy().trim());
            }
            if (request.getIncludeAdjustedWorkdays() != null) {
                row.setIncludeAdjustedWorkdays(request.getIncludeAdjustedWorkdays());
            }
            row.setRemark(trimToNull(request.getRemark()));
        }
        row.setBrandId(project.getBrandId());
        row.setCompanyId(project.getCompanyId());
        row.setUpdatedBy(operator.getId());
        if (create) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        return ProjectSelfMediaScheduleConfigVO.from(row);
    }

    public ProjectSelfMediaScheduleBatchVO getBatch(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        return ProjectSelfMediaScheduleBatchVO.from(batchMapper.selectByProjectAndMonth(projectId, targetMonth));
    }

    public ProjectSelfMediaScheduleBatchDetailVO getBatchDetail(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleBatch batch = batchMapper.selectByProjectAndMonth(projectId, targetMonth);
        if (batch == null) {
            return null;
        }
        batch = settleTerminalGenerationFailure(batch);
        ProjectSelfMediaScheduleBatchDetailVO detail = new ProjectSelfMediaScheduleBatchDetailVO();
        detail.setBatch(ProjectSelfMediaScheduleBatchVO.from(batch));
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        if (payload == null || payload.plans() == null) {
            return detail;
        }
        Map<Long, SelfMediaAccount> accountCache = new LinkedHashMap<>();
        Map<Long, ArticleDraft> articleCache = new LinkedHashMap<>();
        Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> rejectedItems =
                readScheduleRejectedItems(batch.getResultSnapshot());
        for (GenerationPlan plan : payload.plans()) {
            detail.getItems().add(toDetailItem(batch, plan, rejectedItems, accountCache, articleCache));
        }
        return detail;
    }

    public ProjectSelfMediaScheduleBatchDetailVO retryFailedItems(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleBatch batch = batchMapper.selectByProjectAndMonth(projectId, targetMonth);
        if (batch == null) {
            throw new BizException(ERROR_CODE, "自动排期批次不存在");
        }
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        if (payload == null || payload.plans() == null || payload.plans().isEmpty()) {
            throw new BizException(ERROR_CODE, "自动排期批次缺少文章生成计划");
        }
        Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> rejectedItems =
                readScheduleRejectedItems(batch.getResultSnapshot());
        LinkedHashSet<Long> failedGenerationBatchIds = new LinkedHashSet<>();
        List<GeneratedSchedulePlan> rejectedSchedulePlans = new ArrayList<>();
        for (GenerationPlan plan : payload.plans()) {
            BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
            if (task != null && "failed".equals(task.getStatus())) {
                failedGenerationBatchIds.add(plan.generationBatchId());
            } else if (task != null && "success".equals(task.getStatus()) && task.getArticleId() != null
                    && findRejectedItem(rejectedItems, task.getArticleId(), plan) != null
                    && findScheduleForGenerationPlan(batch, plan) == null) {
                rejectedSchedulePlans.add(new GeneratedSchedulePlan(plan, task.getArticleId()));
            }
        }
        if (failedGenerationBatchIds.isEmpty() && rejectedSchedulePlans.isEmpty()) {
            throw new BizException(ERROR_CODE, "当前批次没有可重试的失败项");
        }
        for (Long generationBatchId : failedGenerationBatchIds) {
            generationService.retryFailedSystem(generationBatchId);
        }
        if (!rejectedSchedulePlans.isEmpty()) {
            SelfMediaPublishAutoScheduleResponse retried = createSchedulesFromGenerated(
                    batch,
                    payload,
                    rejectedSchedulePlans,
                    "retry-" + System.currentTimeMillis()
            );
            mergeRetriedScheduleResult(batch, payload, rejectedSchedulePlans, retried);
        }
        if (!failedGenerationBatchIds.isEmpty()) {
            batch.setStatus("processing");
            batch.setFailureMessage("失败项已重新入队，等待文章生成完成");
        }
        batchMapper.updateById(batch);
        return getBatchDetail(projectId, targetMonth);
    }

    public SelfMediaPublishAutoScheduleResponse previewForProject(Long projectId,
                                                                  ProjectSelfMediaAutoScheduleRequest request) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(projectId);
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        List<Long> accountIds = autoRequest.getSelfMediaAccountIds().isEmpty()
                ? selectBrandAccountIds(project.getBrandId())
                : autoRequest.getSelfMediaAccountIds();
        List<AccountPublishPlan> plans = buildAccountPublishPlans(project, autoRequest.getTargetMonth(), accountIds);
        return acceptedResponse(autoRequest, plans.size());
    }

    @Transactional
    public SelfMediaPublishAutoScheduleResponse createForProject(Long projectId,
                                                                 ProjectSelfMediaAutoScheduleRequest request,
                                                                 String triggerMode) {
        Project project = requireProject(projectId);
        SysUser operator = requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(projectId);
        if (config == null || !Boolean.TRUE.equals(config.getAutoScheduleEnabled())) {
            throw new BizException(ERROR_CODE, "项目未开启自媒体自动排期开关");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        ProjectSelfMediaScheduleBatch existed = batchMapper.selectByProjectAndMonth(projectId, autoRequest.getTargetMonth());
        existed = settleTerminalGenerationFailure(existed);
        if (isActiveOrCompletedBatch(existed)) {
            throw new BizException(ERROR_CODE, "该项目本月已创建过自动化排期，不能重复创建");
        }
        return acceptGenerationBatch(project, config, request, triggerMode, operator.getId(), existed);
    }

    public int createDueEnabledProjects(String targetMonth, int limit) {
        int processed = 0;
        for (ProjectSelfMediaScheduleConfig config : configMapper.selectEnabled(Math.max(1, limit))) {
            if (processed >= limit) {
                break;
            }
            try {
                ProjectSelfMediaScheduleBatch existed = settleTerminalGenerationFailure(
                        batchMapper.selectByProjectAndMonth(config.getProjectId(), targetMonth));
                if (isActiveOrCompletedBatch(existed)) {
                    continue;
                }
                Project project = projectMapper.selectById(config.getProjectId());
                if (project == null || project.getDeletedAt() != null) {
                    continue;
                }
                List<Long> accountIds = selectBrandAccountIds(project.getBrandId());
                if (accountIds.isEmpty()) {
                    continue;
                }
                ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
                request.setSelfMediaAccountIds(accountIds);
                request.setTargetMonth(targetMonth);
                request.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
                request.setIncludeAdjustedWorkdays(config.getIncludeAdjustedWorkdays());
                Long operatorId = resolveSystemOperatorId(project, config);
                acceptGenerationBatch(project, config, request, TRIGGER_JOB, operatorId, existed);
                processed++;
            } catch (Exception ex) {
                log.warn("project self-media auto schedule skipped projectId={} month={} error={}",
                        config.getProjectId(), targetMonth, trimMessage(ex.getMessage()));
            }
        }
        return processed;
    }

    public int progressProcessingBatches(int limit) {
        int processed = 0;
        for (ProjectSelfMediaScheduleBatch batch : batchMapper.selectProcessing(Math.max(1, limit))) {
            try {
                if (progressProcessingBatch(batch)) {
                    processed++;
                }
            } catch (Exception ex) {
                batch.setStatus("failed");
                batch.setFailureMessage(trimMessage(ex.getMessage()));
                batchMapper.updateById(batch);
            }
        }
        return processed;
    }

    private Long resolveSystemOperatorId(Project project, ProjectSelfMediaScheduleConfig config) {
        Long operatorId = config.getUpdatedBy() != null && config.getUpdatedBy() > 0 ? config.getUpdatedBy() : config.getCreatedBy();
        if (operatorId == null || operatorId <= 0) {
            operatorId = project.getCreatedBy();
        }
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(ERROR_CODE, "项目自动排期缺少系统操作人");
        }
        return operatorId;
    }

    private SelfMediaPublishAutoScheduleResponse acceptGenerationBatch(Project project,
                                                                       ProjectSelfMediaScheduleConfig config,
                                                                       ProjectSelfMediaAutoScheduleRequest request,
                                                                       String triggerMode,
                                                                       Long operatorId,
                                                                       ProjectSelfMediaScheduleBatch reusableBatch) {
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        List<Long> accountIds = autoRequest.getSelfMediaAccountIds().isEmpty()
                ? selectBrandAccountIds(project.getBrandId())
                : autoRequest.getSelfMediaAccountIds();
        List<AccountPublishPlan> plans = buildAccountPublishPlans(project, autoRequest.getTargetMonth(), accountIds);
        if (plans.isEmpty()) {
            throw new BizException(ERROR_CODE, "该项目本月自媒体平台剩余额度为 0，无法创建自动化排期");
        }

        ProjectSelfMediaScheduleBatch batch = reusableBatch == null
                ? newBatch(project, autoRequest, triggerMode, operatorId)
                : resetReusableBatch(reusableBatch, project, autoRequest, triggerMode, operatorId);
        batch.setStatus("processing");
        batch.setArticleCount(plans.size());
        batch.setAccountCount((int) plans.stream().map(AccountPublishPlan::accountId).distinct().count());
        batch.setPlannedCount(plans.size());
        batch.setCreatedCount(0);
        batch.setRejectedCount(0);
        batch.setGenerationBatchIds(null);
        batch.setResultSnapshot(null);
        batch.setFailureMessage(null);
        batch.setRequestPayload(toJson(Map.of(
                "targetMonth", autoRequest.getTargetMonth(),
                "scheduleStrategy", autoRequest.getScheduleStrategy(),
                "includeAdjustedWorkdays", autoRequest.getIncludeAdjustedWorkdays(),
                "plans", plans
        )));
        if (reusableBatch == null) {
            try {
                batchMapper.insert(batch);
            } catch (DuplicateKeyException ex) {
                throw new BizException(ERROR_CODE, "该项目本月已存在自动化排期批次");
            }
        } else {
            batchMapper.updateById(batch);
        }
        try {
            List<GenerationPlan> generationPlans = createGenerationBatches(project, batch, plans, operatorId);
            batch.setGenerationBatchIds(toJson(generationPlans.stream()
                    .map(GenerationPlan::generationBatchId)
                    .distinct()
                    .toList()));
            batch.setRequestPayload(toJson(new GenerationPayload(
                    autoRequest.getTargetMonth(),
                    autoRequest.getScheduleStrategy(),
                    Boolean.TRUE.equals(autoRequest.getIncludeAdjustedWorkdays()),
                    generationPlans
            )));
            batchMapper.updateById(batch);
            SelfMediaPublishAutoScheduleResponse response = acceptedResponse(autoRequest, plans.size());
            response.setCreated(true);
            return response;
        } catch (RuntimeException ex) {
            batch.setStatus("failed");
            batch.setFailureMessage(trimMessage(ex.getMessage()));
            batchMapper.updateById(batch);
            throw ex;
        }
    }

    private List<GenerationPlan> createGenerationBatches(Project project,
                                                         ProjectSelfMediaScheduleBatch scheduleBatch,
                                                         List<AccountPublishPlan> plans,
                                                         Long operatorId) {
        List<KeywordGroupResult> questions = keywordGroupResultMapper.selectProjectQuestionsByTiers(project.getId(), "'A'");
        List<GenerationPlan> generationPlans = new ArrayList<>();
        for (int start = 0; start < plans.size(); start += GENERATION_BATCH_LIMIT) {
            List<AccountPublishPlan> chunk = plans.subList(start, Math.min(plans.size(), start + GENERATION_BATCH_LIMIT));
            BatchArticleGenerateRequest generationRequest = buildGenerationRequest(project, chunk, questions, start);
            BatchArticleGenerateResponse generation = generationService.createSystemBatch(generationRequest, operatorId);
            List<BatchArticleGenerationTask> tasks = generationTaskMapper.selectList(
                    new LambdaQueryWrapper<BatchArticleGenerationTask>()
                            .eq(BatchArticleGenerationTask::getBatchId, generation.batchId())
                            .orderByAsc(BatchArticleGenerationTask::getArticleIndexInBatch)
            );
            for (int i = 0; i < chunk.size() && i < tasks.size(); i++) {
                AccountPublishPlan plan = chunk.get(i);
                BatchArticleGenerationTask task = tasks.get(i);
                generationPlans.add(new GenerationPlan(
                        generation.batchId(),
                        task.getId(),
                        plan.accountId(),
                        plan.platform()
                ));
            }
        }
        if (generationPlans.size() != plans.size()) {
            throw new BizException(ERROR_CODE, "文章生成任务数量与排期计划数量不一致");
        }
        return generationPlans;
    }

    private BatchArticleGenerateRequest buildGenerationRequest(Project project,
                                                               List<AccountPublishPlan> plans,
                                                               List<KeywordGroupResult> questions,
                                                               int offset) {
        BatchArticleGenerateRequest request = new BatchArticleGenerateRequest();
        request.setProjectId(project.getId());
        request.setTopicSource("manual");
        List<BatchArticleGenerateRequest.TopicConfig> topics = new ArrayList<>();
        Map<String, CompatibleQuestionScenes> sceneCache = new LinkedHashMap<>();
        for (int i = 0; i < plans.size(); i++) {
            AccountPublishPlan plan = plans.get(i);
            TopicSeed question = selectTopicForPlan(project, plan, questions, offset + i, sceneCache);
            BatchArticleGenerateRequest.TopicConfig topic = new BatchArticleGenerateRequest.TopicConfig();
            topic.setTopic(question.topic());
            topic.setTopicAsQuestion(question.topicAsQuestion());
            topic.setQuestionSceneCode(question.sceneCode());
            BatchArticleGenerateRequest.PlatformCount platform = new BatchArticleGenerateRequest.PlatformCount();
            platform.setChannelGroupCode(ArticlePromptChannels.SELF_MEDIA);
            platform.setChannelSubCode(plan.platform());
            platform.setContentStyle(plan.platform());
            platform.setAllocationMode("auto");
            platform.setCount(1);
            topic.setPlatforms(List.of(platform));
            topics.add(topic);
        }
        request.setTopics(topics);
        return request;
    }

    private TopicSeed selectTopicForPlan(Project project,
                                         AccountPublishPlan plan,
                                         List<KeywordGroupResult> questions,
                                         int startIndex,
                                         Map<String, CompatibleQuestionScenes> sceneCache) {
        TemplatePerspectiveService.ResolvedPerspective perspective = perspectiveService.resolve(
                project.getBrandId(),
                ArticlePromptChannels.SELF_MEDIA,
                plan.platform()
        );
        if (!TemplatePerspectiveCodes.isThirdParty(perspective.perspectiveCode())) {
            if (questions.isEmpty()) {
                throw new BizException(ERROR_CODE, "项目缺少 A 级问题，无法生成自媒体排期文章");
            }
            KeywordGroupResult question = questions.get(startIndex % questions.size());
            return new TopicSeed(question.getKeywordText(), question.getKeywordText(), question.getSceneCode());
        }
        String cacheKey = plan.platform() + "|" + perspective.perspectiveCode();
        CompatibleQuestionScenes scenes = sceneCache.computeIfAbsent(cacheKey,
                ignored -> compatibleQuestionScenes(plan.platform(), perspective.perspectiveCode()));
        String sceneCode = scenes.preferredScene(startIndex);
        return thirdPartyTopicSeed(sceneCode, perspective.perspectiveCode());
    }

    private CompatibleQuestionScenes compatibleQuestionScenes(String platform, String perspectiveCode) {
        List<ArticleTemplateAllocationService.TemplateWithVersion> templates = templateAllocationService.activeTemplates(
                ArticlePromptChannels.SELF_MEDIA,
                platform,
                null,
                perspectiveCode
        );
        if (templates.isEmpty()) {
            throw new BizException(ERROR_CODE, "特殊视角缺少启用模板: channelGroup="
                    + ArticlePromptChannels.SELF_MEDIA
                    + ", channelSub=" + platform
                    + ", perspective=" + perspectiveCode);
        }
        boolean acceptsAny = templates.stream()
                .anyMatch(item -> !StringUtils.hasText(item.template().getQuestionSceneCode()));
        Set<String> sceneCodes = templates.stream()
                .map(item -> trimToNull(item.template().getQuestionSceneCode()))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new CompatibleQuestionScenes(acceptsAny, sceneCodes);
    }

    private TopicSeed thirdPartyTopicSeed(String sceneCode, String perspectiveCode) {
        String scene = trimToNull(sceneCode);
        String topic = switch (scene == null ? "" : scene) {
            case "brand" -> TemplatePerspectiveCodes.REVIEW_RECOMMEND.equals(perspectiveCode)
                    ? "这个品牌是否适合当前需求"
                    : "这个品牌在行业里是什么角色";
            case "decision" -> "这类服务应该怎么选";
            case "compare" -> "这类方案应该怎么比较";
            case "qa" -> "这个行业常见问题有哪些";
            case "function" -> "这类服务能解决什么问题";
            default -> TemplatePerspectiveCodes.REVIEW_RECOMMEND.equals(perspectiveCode)
                    ? "当前行业里哪些选择更值得关注"
                    : "当前行业趋势和选择逻辑是什么";
        };
        return new TopicSeed(topic, topic, scene);
    }

    private record CompatibleQuestionScenes(boolean acceptsAny, Set<String> sceneCodes) {
        String preferredScene(int index) {
            if (sceneCodes.isEmpty()) {
                return null;
            }
            List<String> scenes = List.copyOf(sceneCodes);
            return scenes.get(Math.floorMod(index, scenes.size()));
        }
    }

    private record TopicSeed(String topic, String topicAsQuestion, String sceneCode) {
    }

    private boolean progressProcessingBatch(ProjectSelfMediaScheduleBatch batch) {
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        if (payload == null || payload.plans() == null || payload.plans().isEmpty()) {
            throw new BizException(ERROR_CODE, "自动排期批次缺少文章生成计划");
        }
        List<GeneratedSchedulePlan> generated = new ArrayList<>();
        int failed = 0;
        for (GenerationPlan plan : payload.plans()) {
            BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
            if (task == null) {
                failed++;
                continue;
            }
            if ("success".equals(task.getStatus()) && task.getArticleId() != null) {
                generated.add(new GeneratedSchedulePlan(plan, task.getArticleId()));
            } else if ("failed".equals(task.getStatus())) {
                failed++;
            } else {
                return false;
            }
        }
        if (generated.isEmpty()) {
            batch.setStatus("failed");
            batch.setCreatedCount(0);
            batch.setRejectedCount(failed);
            batch.setFailureMessage("自动排期文章生成全部失败");
            batchMapper.updateById(batch);
            return true;
        }
        SelfMediaPublishAutoScheduleResponse response = createSchedulesFromGenerated(batch, payload, generated);
        int created = response.getCreatedSchedules().size() + response.getExistingSchedules().size();
        int rejected = response.getRejectedItems().size() + failed;
        batch.setStatus(rejected > 0 ? "partial_failed" : "created");
        batch.setCreatedCount(created);
        batch.setRejectedCount(rejected);
        batch.setResultSnapshot(toJson(response));
        batch.setFailureMessage(rejected > 0 ? "部分文章生成或排期失败" : null);
        batchMapper.updateById(batch);
        return true;
    }

    private SelfMediaPublishAutoScheduleResponse createSchedulesFromGenerated(ProjectSelfMediaScheduleBatch batch,
                                                                             GenerationPayload payload,
                                                                             List<GeneratedSchedulePlan> generated) {
        return createSchedulesFromGenerated(batch, payload, generated, null);
    }

    private SelfMediaPublishAutoScheduleResponse createSchedulesFromGenerated(ProjectSelfMediaScheduleBatch batch,
                                                                             GenerationPayload payload,
                                                                             List<GeneratedSchedulePlan> generated,
                                                                             String requestKeySuffix) {
        YearMonth targetMonth = YearMonth.parse(payload.targetMonth());
        List<BusinessCalendarService.PublishSlot> slots = selectSlotsEvenlyByPlatform(
                targetMonth,
                generated,
                payload.includeAdjustedWorkdays()
        );
        SelfMediaPublishAutoScheduleResponse response = acceptedResponse(
                batch.getBrandId(),
                payload.targetMonth(),
                payload.scheduleStrategy(),
                generated.size()
        );
        for (int i = 0; i < generated.size(); i++) {
            GeneratedSchedulePlan plan = generated.get(i);
            BusinessCalendarService.PublishSlot slot = slots.get(i);
            String scheduleStrategy = resolveItemStrategy(payload.scheduleStrategy(), plan.plan().platform());
            LocalDateTime plannedPublishAt = resolvePlannedPublishAt(slot, plan.plan().platform(), scheduleStrategy);
            SelfMediaPublishScheduleCreateRequest request = new SelfMediaPublishScheduleCreateRequest();
            request.setBrandId(batch.getBrandId());
            request.setArticleIds(List.of(plan.articleId()));
            request.setSelfMediaAccountIds(List.of(plan.plan().selfMediaAccountId()));
            request.setWindowStart(plannedPublishAt);
            request.setWindowEnd(plannedPublishAt);
            request.setExecutionWindowStart(slot.plannedAt());
            request.setExecutionWindowEnd(slot.plannedAt());
            request.setScheduleStrategy(scheduleStrategy);
            request.setMinIntervalMinutes(3);
            ensureArticleCoverIfRequired(batch.getBrandId(), plan.plan().platform(), plan.articleId());
            try {
                SelfMediaPublishScheduleCreateResponse created = scheduleService.createSystemSchedules(
                        request,
                        projectAutoScheduleRequestKey(batch, plan.plan(), requestKeySuffix),
                        batch.getCreatedBy()
                );
                response.getCreatedSchedules().addAll(created.getCreatedSchedules());
                response.getExistingSchedules().addAll(created.getExistingSchedules());
                response.getRejectedItems().addAll(created.getRejectedItems());
            } catch (RuntimeException ex) {
                response.getRejectedItems().add(SelfMediaPublishScheduleRejectedItemVO.builder()
                        .articleId(plan.articleId())
                        .selfMediaAccountId(plan.plan().selfMediaAccountId())
                        .platform(plan.plan().platform())
                        .code("PROJECT_AUTO_SCHEDULE_CREATE_FAILED")
                        .message(trimMessage(ex.getMessage()))
                        .build());
            }
        }
        response.setCreated(true);
        response.setCreatedSchedules(response.getCreatedSchedules().stream().distinct().toList());
        response.setExistingSchedules(response.getExistingSchedules().stream().distinct().toList());
        response.setPlannedCount(response.getCreatedSchedules().size() + response.getExistingSchedules().size());
        response.setRejectedCount(response.getRejectedItems().size());
        return response;
    }

    private void mergeRetriedScheduleResult(ProjectSelfMediaScheduleBatch batch,
                                            GenerationPayload payload,
                                            List<GeneratedSchedulePlan> retriedPlans,
                                            SelfMediaPublishAutoScheduleResponse retried) {
        SelfMediaPublishAutoScheduleResponse snapshot = readAutoScheduleResponse(batch.getResultSnapshot());
        if (snapshot == null) {
            snapshot = acceptedResponse(batch.getBrandId(), payload.targetMonth(), payload.scheduleStrategy(), payload.plans().size());
        }
        LinkedHashSet<ScheduleRejectedKey> retriedKeys = new LinkedHashSet<>();
        for (GeneratedSchedulePlan plan : retriedPlans) {
            retriedKeys.add(new ScheduleRejectedKey(
                    plan.articleId(),
                    plan.plan().selfMediaAccountId(),
                    normalizePlatform(plan.plan().platform())
            ));
        }
        List<SelfMediaPublishScheduleRejectedItemVO> remainingRejected = new ArrayList<>();
        for (SelfMediaPublishScheduleRejectedItemVO item : snapshot.getRejectedItems()) {
            ScheduleRejectedKey key = item == null ? null : new ScheduleRejectedKey(
                    item.getArticleId(),
                    item.getSelfMediaAccountId(),
                    normalizePlatform(item.getPlatform())
            );
            if (key == null || !retriedKeys.contains(key)) {
                remainingRejected.add(item);
            }
        }
        remainingRejected.addAll(retried.getRejectedItems());
        snapshot.setRejectedItems(remainingRejected);
        snapshot.getCreatedSchedules().addAll(retried.getCreatedSchedules());
        snapshot.getExistingSchedules().addAll(retried.getExistingSchedules());
        snapshot.setCreatedSchedules(snapshot.getCreatedSchedules().stream().distinct().toList());
        snapshot.setExistingSchedules(snapshot.getExistingSchedules().stream().distinct().toList());
        snapshot.setRejectedCount(snapshot.getRejectedItems().size());
        snapshot.setPlannedCount(snapshot.getCreatedSchedules().size() + snapshot.getExistingSchedules().size());

        int failedGenerationCount = countFailedGenerationTasks(payload);
        int created = (batch.getCreatedCount() == null ? 0 : batch.getCreatedCount())
                + retried.getCreatedSchedules().size()
                + retried.getExistingSchedules().size();
        int rejected = failedGenerationCount + snapshot.getRejectedItems().size();
        batch.setCreatedCount(created);
        batch.setRejectedCount(rejected);
        batch.setResultSnapshot(toJson(snapshot));
        batch.setStatus(rejected > 0 ? "partial_failed" : "created");
        batch.setFailureMessage(rejected > 0 ? "部分文章生成或排期失败" : null);
    }

    private int countFailedGenerationTasks(GenerationPayload payload) {
        if (payload == null || payload.plans() == null) {
            return 0;
        }
        int failed = 0;
        for (GenerationPlan plan : payload.plans()) {
            BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
            if (task != null && "failed".equals(task.getStatus())) {
                failed++;
            }
        }
        return failed;
    }

    private List<BusinessCalendarService.PublishSlot> selectSlotsEvenlyByPlatform(YearMonth targetMonth,
                                                                                  List<GeneratedSchedulePlan> generated,
                                                                                  boolean includeAdjustedWorkdays) {
        Map<String, List<Integer>> indexesByPlatform = new LinkedHashMap<>();
        for (int i = 0; i < generated.size(); i++) {
            String platform = normalizePlatform(generated.get(i).plan().platform());
            indexesByPlatform.computeIfAbsent(firstText(platform, "unknown"), ignored -> new ArrayList<>()).add(i);
        }
        List<BusinessCalendarService.PublishSlot> result = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            result.add(null);
        }
        for (List<Integer> indexes : indexesByPlatform.values()) {
            List<BusinessCalendarService.PublishSlot> platformSlots = businessCalendarService.selectEvenly(
                    targetMonth,
                    indexes.size(),
                    includeAdjustedWorkdays
            );
            for (int i = 0; i < indexes.size(); i++) {
                result.set(indexes.get(i), platformSlots.get(i));
            }
        }
        return result;
    }

    private LocalDateTime resolvePlannedPublishAt(BusinessCalendarService.PublishSlot executionSlot,
                                                  String platform,
                                                  String scheduleStrategy) {
        int leadMinutes = Math.max(0, scheduleAdapterRouter.rules(platform, scheduleStrategy).fillLeadMinutes());
        return executionSlot.plannedAt().plusMinutes(leadMinutes);
    }

    private void ensureArticleCoverIfRequired(Long brandId, String platform, Long articleId) {
        if (articleId == null) {
            return;
        }
        boolean requiresCoverUpload = scheduleAdapterRouter.contract(platform)
                .map(contract -> contract.requiresCoverUpload())
                .orElse(false);
        if (!requiresCoverUpload) {
            return;
        }
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null || StringUtils.hasText(article.getCoverImageUrl())) {
            return;
        }
        String coverUrl = coverSelectionService.selectRandomCoverUrl(coverBrandId(brandId, article));
        if (!StringUtils.hasText(coverUrl)) {
            return;
        }
        article.setCoverImageUrl(coverUrl);
        articleDraftMapper.updateById(article);
    }

    private Long coverBrandId(Long sourceBrandId, ArticleDraft article) {
        return article != null && article.getSubjectBrandId() != null ? article.getSubjectBrandId() : sourceBrandId;
    }

    private List<AccountPublishPlan> buildAccountPublishPlans(Project project, String targetMonth, List<Long> accountIds) {
        YearMonth month = YearMonth.parse(targetMonth);
        LocalDateTime periodStart = month.atDay(1).atStartOfDay();
        LocalDateTime periodEnd = month.plusMonths(1).atDay(1).atStartOfDay();
        Map<String, List<SelfMediaAccount>> accountsByPlatform = new LinkedHashMap<>();
        for (Long accountId : new LinkedHashSet<>(accountIds)) {
            SelfMediaAccount account = selfMediaAccountMapper.selectById(accountId);
            if (account == null || account.getBrandId() == null || !account.getBrandId().equals(project.getBrandId())) {
                continue;
            }
            if (!"active".equals(account.getStatus()) || account.getDeletedAt() != null) {
                continue;
            }
            String platform = normalizePlatform(account.getPlatform());
            if (!StringUtils.hasText(platform)) {
                continue;
            }
            accountsByPlatform.computeIfAbsent(platform, ignored -> new ArrayList<>()).add(account);
        }
        List<AccountPublishPlan> plans = new ArrayList<>();
        accountsByPlatform.forEach((platform, accounts) -> {
            CompanyChannelQuotaService.DistributionQuotaView quota =
                    companyChannelQuotaService.selfMediaDistributionQuota(project.getCompanyId(), platform);
            long activeSchedules = selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                    project.getBrandId(),
                    platform,
                    periodStart,
                    periodEnd,
                    new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES)
            );
            int occupied = Math.max(quota.usedCount(), Math.toIntExact(Math.min(activeSchedules, Integer.MAX_VALUE)));
            int remaining = Math.max(0, quota.quotaLimit() - occupied);
            for (int i = 0; i < remaining; i++) {
                SelfMediaAccount account = accounts.get(i % accounts.size());
                plans.add(new AccountPublishPlan(account.getId(), platform));
            }
        });
        return plans;
    }

    private SelfMediaPublishAutoScheduleResponse acceptedResponse(SelfMediaPublishAutoScheduleRequest request, int count) {
        return acceptedResponse(request.getBrandId(), request.getTargetMonth(), request.getScheduleStrategy(), count);
    }

    private SelfMediaPublishAutoScheduleResponse acceptedResponse(Long brandId, String targetMonth, String strategy, int count) {
        SelfMediaPublishAutoScheduleResponse response = new SelfMediaPublishAutoScheduleResponse();
        response.setBrandId(brandId);
        response.setTargetMonth(targetMonth);
        response.setScheduleStrategy(strategy);
        response.setRequestedCount(count);
        response.setPlannedCount(count);
        response.setRejectedCount(0);
        return response;
    }

    private GenerationPayload readGenerationPayload(String value) {
        try {
            return objectMapper.readValue(value, GenerationPayload.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private SelfMediaPublishAutoScheduleResponse readAutoScheduleResponse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SelfMediaPublishAutoScheduleResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private ProjectSelfMediaScheduleBatchDetailVO.Item toDetailItem(ProjectSelfMediaScheduleBatch batch,
                                                                    GenerationPlan plan,
                                                                    Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> rejectedItems,
                                                                    Map<Long, SelfMediaAccount> accountCache,
                                                                    Map<Long, ArticleDraft> articleCache) {
        ProjectSelfMediaScheduleBatchDetailVO.Item item = new ProjectSelfMediaScheduleBatchDetailVO.Item();
        item.setGenerationBatchId(plan.generationBatchId());
        item.setGenerationTaskId(plan.generationTaskId());
        item.setSelfMediaAccountId(plan.selfMediaAccountId());
        item.setPlatform(plan.platform());

        SelfMediaAccount account = accountCache.computeIfAbsent(plan.selfMediaAccountId(), selfMediaAccountMapper::selectById);
        if (account != null) {
            item.setSelfMediaAccountName(account.getAccountName());
        }

        BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
        if (task != null) {
            item.setSourceBrandId(task.getSourceBrandId());
            item.setSubjectBrandId(task.getSubjectBrandId());
            item.setSubjectProjectId(task.getSubjectProjectId());
            item.setSourceBrandName(resolveBrandName(task.getSourceBrandId()));
            item.setSubjectBrandName(resolveBrandName(task.getSubjectBrandId()));
            item.setGenerationStatus(task.getStatus());
            item.setGenerationErrorMessage(task.getErrorMessage());
            item.setGenerationTopic(task.getTopicAsQuestion());
            if (!StringUtils.hasText(item.getGenerationTopic())) {
                item.setGenerationTopic(task.getTopic());
            }
            item.setGenerationArticleType(task.getArticleType());
            item.setGenerationCreatedAt(task.getCreatedAt());
            item.setGenerationUpdatedAt(task.getUpdatedAt());
            item.setGenerationStartedAt(task.getStartedAt());
            item.setGenerationFinishedAt(task.getFinishedAt());
            item.setArticleId(task.getArticleId());
            if (task.getArticleId() != null) {
                ArticleDraft article = articleCache.computeIfAbsent(task.getArticleId(), articleDraftMapper::selectById);
                if (article != null) {
                    item.setArticleTitle(article.getTitle());
                }
            }
        }

        SelfMediaPublishSchedule schedule = findScheduleForGenerationPlan(batch, plan);
        if (schedule != null) {
            item.setScheduleId(schedule.getId());
            item.setScheduleStatus(schedule.getStatus());
            item.setPlannedPublishAt(schedule.getPlannedPublishAt());
            item.setQueueKind(schedule.getQueueKind());
            item.setAttemptCount(schedule.getAttemptCount());
            item.setMaxAttempts(schedule.getMaxAttempts());
            item.setLastAttemptAt(schedule.getLastAttemptAt());
            item.setNextAttemptAt(schedule.getNextAttemptAt());
            item.setLockedUntil(schedule.getLockedUntil());
            item.setScheduleFailureCode(schedule.getFailureCode());
            item.setScheduleFailureMessage(schedule.getFailureMessage());
        } else {
            SelfMediaPublishScheduleRejectedItemVO rejected = findRejectedItem(rejectedItems, item.getArticleId(), plan);
            if (rejected != null) {
                item.setScheduleStatus("rejected");
                item.setScheduleFailureCode(rejected.getCode());
                item.setScheduleFailureMessage(firstText(rejected.getMessage(), rejected.getCode()));
            }
        }
        return item;
    }

    private String resolveBrandName(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(brandId);
        return brand == null ? null : brand.getBrandName();
    }

    private Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> readScheduleRejectedItems(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            SelfMediaPublishAutoScheduleResponse response =
                    objectMapper.readValue(value, SelfMediaPublishAutoScheduleResponse.class);
            if (response.getRejectedItems() == null || response.getRejectedItems().isEmpty()) {
                return Map.of();
            }
            Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> result = new LinkedHashMap<>();
            for (SelfMediaPublishScheduleRejectedItemVO item : response.getRejectedItems()) {
                if (item == null || item.getArticleId() == null || item.getSelfMediaAccountId() == null) {
                    continue;
                }
                result.put(new ScheduleRejectedKey(
                        item.getArticleId(),
                        item.getSelfMediaAccountId(),
                        normalizePlatform(item.getPlatform())
                ), item);
            }
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private SelfMediaPublishScheduleRejectedItemVO findRejectedItem(
            Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> rejectedItems,
            Long articleId,
            GenerationPlan plan
    ) {
        if (rejectedItems.isEmpty() || articleId == null || plan == null) {
            return null;
        }
        return rejectedItems.get(new ScheduleRejectedKey(
                articleId,
                plan.selfMediaAccountId(),
                normalizePlatform(plan.platform())
        ));
    }

    private SelfMediaPublishSchedule findScheduleForGenerationPlan(ProjectSelfMediaScheduleBatch batch, GenerationPlan plan) {
        SelfMediaPublishScheduleRequest request = selfMediaPublishScheduleRequestMapper.selectByRequestKey(
                batch.getBrandId(),
                projectAutoScheduleRequestKey(batch, plan)
        );
        if (request != null && request.getId() != null) {
            List<SelfMediaPublishSchedule> schedules = selfMediaPublishScheduleMapper.selectByRequestId(request.getId());
            if (!schedules.isEmpty()) {
                return schedules.get(0);
            }
        }
        BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
        if (task == null || task.getArticleId() == null) {
            return null;
        }
        return selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(
                task.getArticleId(),
                plan.selfMediaAccountId(),
                normalizePlatform(plan.platform())
        );
    }

    private String projectAutoScheduleRequestKey(ProjectSelfMediaScheduleBatch batch, GenerationPlan plan) {
        return projectAutoScheduleRequestKey(batch, plan, null);
    }

    private String projectAutoScheduleRequestKey(ProjectSelfMediaScheduleBatch batch, GenerationPlan plan, String suffix) {
        String base = "project-auto-" + batch.getId() + "-" + plan.generationTaskId();
        if (!StringUtils.hasText(suffix)) {
            return base;
        }
        return base + "-" + suffix;
    }

    private record ScheduleRejectedKey(Long articleId, Long selfMediaAccountId, String platform) {
    }

    private List<Long> selectBrandAccountIds(Long brandId) {
        if (brandId == null || brandId <= 0) {
            return List.of();
        }
        return selfMediaAccountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                        .eq(SelfMediaAccount::getBrandId, brandId)
                        .eq(SelfMediaAccount::getStatus, "active")
                        .isNull(SelfMediaAccount::getDeletedAt)
                        .orderByAsc(SelfMediaAccount::getId)
                        .last("LIMIT 50"))
                .stream()
                .map(SelfMediaAccount::getId)
                .toList();
    }

    private SelfMediaPublishAutoScheduleRequest toAutoRequest(Project project,
                                                              ProjectSelfMediaScheduleConfig config,
                                                              ProjectSelfMediaAutoScheduleRequest request) {
        if (request == null) {
            throw new BizException(ERROR_CODE, "request is required");
        }
        if (project.getBrandId() == null || project.getBrandId() <= 0) {
            throw new BizException(ERROR_CODE, "项目未绑定品牌，不能创建自媒体排期");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = new SelfMediaPublishAutoScheduleRequest();
        autoRequest.setBrandId(project.getBrandId());
        autoRequest.setArticleIds(request.getArticleIds() == null ? List.of() : request.getArticleIds());
        autoRequest.setSelfMediaAccountIds(request.getSelfMediaAccountIds() == null ? List.of() : request.getSelfMediaAccountIds());
        autoRequest.setTargetMonth(request.getTargetMonth());
        autoRequest.setScheduleStrategy(firstText(
                request.getScheduleStrategy(),
                SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC
        ));
        autoRequest.setIncludeAdjustedWorkdays(request.getIncludeAdjustedWorkdays() != null
                ? request.getIncludeAdjustedWorkdays()
                : config != null && Boolean.TRUE.equals(config.getIncludeAdjustedWorkdays()));
        return autoRequest;
    }

    private ProjectSelfMediaScheduleBatch newBatch(Project project,
                                                   SelfMediaPublishAutoScheduleRequest request,
                                                   String triggerMode,
                                                   Long operatorId) {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setProjectId(project.getId());
        batch.setBrandId(project.getBrandId());
        batch.setCompanyId(project.getCompanyId());
        batch.setTargetMonth(request.getTargetMonth());
        batch.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MANUAL);
        batch.setStatus("created");
        batch.setScheduleStrategy(request.getScheduleStrategy());
        batch.setArticleCount(request.getArticleIds() == null ? 0 : request.getArticleIds().size());
        batch.setAccountCount(request.getSelfMediaAccountIds() == null ? 0 : request.getSelfMediaAccountIds().size());
        batch.setPlannedCount(0);
        batch.setCreatedCount(0);
        batch.setRejectedCount(0);
        batch.setCreatedBy(operatorId);
        batch.setUpdatedBy(operatorId);
        return batch;
    }

    private ProjectSelfMediaScheduleBatch resetReusableBatch(ProjectSelfMediaScheduleBatch batch,
                                                             Project project,
                                                             SelfMediaPublishAutoScheduleRequest request,
                                                             String triggerMode,
                                                             Long operatorId) {
        batch.setProjectId(project.getId());
        batch.setBrandId(project.getBrandId());
        batch.setCompanyId(project.getCompanyId());
        batch.setTargetMonth(request.getTargetMonth());
        batch.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MANUAL);
        batch.setScheduleStrategy(request.getScheduleStrategy());
        batch.setUpdatedBy(operatorId);
        return batch;
    }

    private ProjectSelfMediaScheduleBatch settleTerminalGenerationFailure(ProjectSelfMediaScheduleBatch batch) {
        if (batch == null || !"processing".equals(batch.getStatus())) {
            return batch;
        }
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        if (payload == null || payload.plans() == null || payload.plans().isEmpty()) {
            return batch;
        }
        int success = 0;
        int failed = 0;
        for (GenerationPlan plan : payload.plans()) {
            BatchArticleGenerationTask task = generationTaskMapper.selectById(plan.generationTaskId());
            if (task == null || "failed".equals(task.getStatus())) {
                failed++;
                continue;
            }
            if ("success".equals(task.getStatus()) && task.getArticleId() != null) {
                success++;
                continue;
            }
            return batch;
        }
        if (success == 0 && failed == payload.plans().size()) {
            batch.setStatus("failed");
            batch.setCreatedCount(0);
            batch.setRejectedCount(failed);
            batch.setFailureMessage("自动排期文章生成全部失败");
            batchMapper.updateById(batch);
        }
        return batch;
    }

    private ProjectSelfMediaScheduleConfig defaultConfig(Project project) {
        ProjectSelfMediaScheduleConfig row = new ProjectSelfMediaScheduleConfig();
        row.setProjectId(project.getId());
        row.setBrandId(project.getBrandId());
        row.setCompanyId(project.getCompanyId());
        row.setAutoScheduleEnabled(false);
        row.setDefaultScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
        row.setIncludeAdjustedWorkdays(false);
        return row;
    }

    private Project requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BizException(ERROR_CODE, "projectId is required");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(ERROR_CODE, "项目不存在");
        }
        if (project.getCompanyId() == null || project.getCompanyId() <= 0) {
            throw new BizException(ERROR_CODE, "项目未关联有效客户");
        }
        return project;
    }

    private SysUser requireProjectOperate(Project project) {
        SysUser operator = currentUserService.requireCurrentUser();
        if (project.getBrandId() != null && project.getBrandId() > 0) {
            brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        }
        return operator;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveItemStrategy(String requestedStrategy, String platform) {
        String normalized = firstText(requestedStrategy, SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
        if (!SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC.equals(normalized)) {
            return normalized;
        }
        return scheduleAdapterRouter.contract(platform)
                .map(contract -> contract.supportsBackendDelayedPublish()
                        ? SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH
                        : SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE)
                .orElse(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE);
    }

    private String trimMessage(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }

    private boolean isActiveOrCompletedBatch(ProjectSelfMediaScheduleBatch batch) {
        if (batch == null || !StringUtils.hasText(batch.getStatus())) {
            return false;
        }
        return List.of("processing", "created", "partial_failed").contains(batch.getStatus());
    }

    private String normalizePlatform(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return ArticlePromptChannels.canonicalSelfMediaQuotaPlatform(value.trim().toLowerCase(Locale.ROOT));
    }

    private record AccountPublishPlan(Long accountId, String platform) {
    }

    private record GenerationPlan(Long generationBatchId,
                                  Long generationTaskId,
                                  Long selfMediaAccountId,
                                  String platform) {
    }

    private record GenerationPayload(String targetMonth,
                                     String scheduleStrategy,
                                     boolean includeAdjustedWorkdays,
                                     List<GenerationPlan> plans) {
    }

    private record GeneratedSchedulePlan(GenerationPlan plan, Long articleId) {
    }
}
