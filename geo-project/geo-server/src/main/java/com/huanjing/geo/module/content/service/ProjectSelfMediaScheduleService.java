package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.SelfMediaPublishFailureCodes;
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
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleCarryOver;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaSchedulePlan;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleCarryOverMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaSchedulePlanMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchDetailVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleConfigVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleItemVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleRejectedItemVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSelfMediaScheduleService {
    private static final int ERROR_CODE = 70043;
    private static final int GENERATION_BATCH_LIMIT = 30;
    private static final int AUTO_COMPENSATION_MAX_ATTEMPTS = 3;
    private static final int LOCAL_AGENT_ONLINE_WINDOW_MINUTES = 10;
    private static final String ERROR_CAPACITY_INSUFFICIENT = "SELF_MEDIA_SCHEDULE_CAPACITY_INSUFFICIENT";
    private static final String ERROR_COMPRESS_NOT_SUPPORTED = "SELF_MEDIA_SCHEDULE_COMPRESS_NOT_SUPPORTED";
    private static final String ERROR_DECISION_REASON_REQUIRED = "SELF_MEDIA_SCHEDULE_DECISION_REASON_REQUIRED";
    private static final String ERROR_PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN =
            "PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN";
    private static final String ERROR_PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN =
            "PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN";
    private static final String STRATEGY_STRICT_CURRENT_MONTH = "strict_current_month";
    private static final String STRATEGY_CARRY_OVER = "carry_over";
    private static final String STRATEGY_COMPRESSED_CURRENT_MONTH = "compressed_current_month";
    private static final String CARRY_OVER_STATUS_PENDING = "pending";
    private static final String CARRY_OVER_STATUS_CONSUMED = "consumed";
    private static final String PERM_SELF_MEDIA_SCHEDULE_LATE_START_DECIDE =
            "content.self_media_schedule.late_start_decide";
    public static final String TRIGGER_MANUAL = "manual";
    public static final String TRIGGER_JOB = "job";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_CREATED = "created";
    private static final String STATUS_PARTIAL_FAILED = "partial_failed";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_SKIPPED = "skipped";

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final ProjectSelfMediaScheduleConfigMapper configMapper;
    private final ProjectSelfMediaScheduleBatchMapper batchMapper;
    private final ProjectSelfMediaScheduleCarryOverMapper carryOverMapper;
    private final ProjectSelfMediaSchedulePlanMapper planMapper;
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
    private final LocalAgentSessionMapper localAgentSessionMapper;
    private final ObjectMapper objectMapper;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private Clock clock = Clock.systemDefaultZone();

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
        parseTargetMonth(targetMonth);
        return ProjectSelfMediaScheduleBatchVO.from(batchMapper.selectByProjectAndMonth(projectId, targetMonth));
    }

    public ProjectSelfMediaScheduleBatchDetailVO getBatchDetail(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
        ProjectSelfMediaScheduleBatch batch = batchMapper.selectByProjectAndMonth(projectId, targetMonth);
        if (batch == null) {
            return null;
        }
        batch = settleTerminalGenerationFailure(batch);
        ProjectSelfMediaScheduleBatchDetailVO detail = new ProjectSelfMediaScheduleBatchDetailVO();
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        if (payload == null || payload.plans() == null) {
            detail.setBatch(ProjectSelfMediaScheduleBatchVO.from(batch));
            return detail;
        }
        Map<Long, SelfMediaAccount> accountCache = new LinkedHashMap<>();
        Map<Long, ArticleDraft> articleCache = new LinkedHashMap<>();
        Map<ScheduleRejectedKey, SelfMediaPublishScheduleRejectedItemVO> rejectedItems =
                readScheduleRejectedItems(batch.getResultSnapshot());
        for (GenerationPlan plan : payload.plans()) {
            detail.getItems().add(toDetailItem(batch, plan, rejectedItems, accountCache, articleCache));
        }
        batch = reconcileBatchSummaryFromDetail(batch, detail.getItems());
        detail.setBatch(ProjectSelfMediaScheduleBatchVO.from(batch));
        detail.setFailureSummaries(buildFailureSummaries(detail.getItems()));
        detail.setStatusRules(scheduleStatusRules());
        detail.setActionPreview(buildBatchActionPreview(detail.getItems(), targetMonth));
        return detail;
    }

    public BusinessCalendarService.CalendarFileStatus calendarStatus(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        YearMonth month = parseTargetMonth(targetMonth);
        return businessCalendarService.fileStatus(month.getYear());
    }

    public ProjectSelfMediaScheduleBatchDetailVO retryFailedItems(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
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
            } else if (isGeneratedWithoutSchedule(batch, plan, task)) {
                SelfMediaPublishScheduleRejectedItemVO rejected = findRejectedItem(rejectedItems, task.getArticleId(), plan);
                if (rejected != null || task.getArticleId() != null) {
                    rejectedSchedulePlans.add(new GeneratedSchedulePlan(plan, task.getArticleId()));
                }
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

    public ProjectSelfMediaScheduleBatchDetailVO retryAbnormalScheduleItems(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
        ProjectSelfMediaScheduleBatchDetailVO detail = getBatchDetail(projectId, targetMonth);
        if (detail == null || detail.getItems() == null || detail.getItems().isEmpty()) {
            throw new BizException(ERROR_CODE, "自动排期批次不存在或没有可处理明细");
        }
        int retried = 0;
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : detail.getItems()) {
            if (item.getScheduleId() == null || !isRetryableScheduleStatus(item.getScheduleStatus())) {
                continue;
            }
            scheduleService.retryNow(item.getScheduleId());
            retried++;
        }
        if (retried <= 0) {
            throw new BizException(ERROR_CODE, "当前批次没有可重新处理的异常排期");
        }
        return getBatchDetail(project.getId(), targetMonth);
    }

    public ProjectSelfMediaScheduleBatchDetailVO markAbnormalScheduleItemsManualRequired(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
        ProjectSelfMediaScheduleBatchDetailVO detail = getBatchDetail(projectId, targetMonth);
        if (detail == null || detail.getItems() == null || detail.getItems().isEmpty()) {
            throw new BizException(ERROR_CODE, "自动排期批次不存在或没有可处理明细");
        }
        int marked = 0;
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : detail.getItems()) {
            if (item.getScheduleId() == null || !isManualRequiredMarkableScheduleStatus(item.getScheduleStatus())) {
                continue;
            }
            scheduleService.markManualRequired(item.getScheduleId(), "项目自动排期批量转人工处理");
            marked++;
        }
        if (marked <= 0) {
            throw new BizException(ERROR_CODE, "当前批次没有可转人工处理的异常排期");
        }
        return getBatchDetail(project.getId(), targetMonth);
    }

    @Transactional
    public ProjectSelfMediaScheduleBatchDetailVO rescheduleAbnormalScheduleItemsToNextMonth(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
        ProjectSelfMediaScheduleBatch batch = batchMapper.selectByProjectAndMonth(projectId, targetMonth);
        if (batch == null) {
            throw new BizException(ERROR_CODE, "自动排期批次不存在");
        }
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        ProjectSelfMediaScheduleBatchDetailVO detail = getBatchDetail(projectId, targetMonth);
        List<ProjectSelfMediaScheduleBatchDetailVO.Item> candidates = detail == null ? List.of() : detail.getItems().stream()
                .filter(item -> item.getScheduleId() != null && isRetryableScheduleStatus(item.getScheduleStatus()))
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(ERROR_CODE, "当前批次没有可改期的异常排期");
        }
        rejectPublishResultCheckBatchAction(
                candidates,
                ERROR_PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN,
                "发布结果未知的排期不得改期到下月，请先重新校验发布结果"
        );
        YearMonth nextMonth = parseTargetMonth(targetMonth).plusMonths(1);
        List<GeneratedSchedulePlan> reschedulePlans = candidates.stream()
                .map(item -> new GeneratedSchedulePlan(
                        new GenerationPlan(
                                item.getGenerationBatchId(),
                                item.getGenerationTaskId(),
                                item.getSelfMediaAccountId(),
                                item.getPlatform()
                        ),
                        item.getArticleId()
                ))
                .toList();
        List<BusinessCalendarService.PublishSlot> slots = selectSlotsEvenlyByPlatform(
                nextMonth,
                reschedulePlans,
                payload == null ? null : payload.scheduleStrategy(),
                payload != null && payload.includeAdjustedWorkdays()
        );
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            ProjectSelfMediaScheduleBatchDetailVO.Item item = candidates.get(i);
            SelfMediaPublishSchedule row = selfMediaPublishScheduleMapper.selectByIdForUpdate(item.getScheduleId());
            if (row == null || !isRetryableScheduleStatus(row.getStatus())) {
                continue;
            }
            rejectPublishResultCheckBatchAction(
                    row,
                    ERROR_PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN,
                    "发布结果未知的排期不得改期到下月，请先重新校验发布结果"
            );
            BusinessCalendarService.PublishSlot slot = slots.get(i);
            String strategy = resolveItemStrategy(firstText(row.getScheduleStrategy(), payload == null ? null : payload.scheduleStrategy()), row.getPlatform());
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
            row.setScheduleStrategy(strategy);
            row.setNextAttemptAt(slot.plannedAt());
            row.setPlannedPublishAt(resolvePlannedPublishAt(slot, row.getPlatform(), strategy));
            row.setPlatformScheduledAt(row.getPlannedPublishAt());
            row.setLockedUntil(null);
            row.setFailureCode("RESCHEDULED_TO_NEXT_MONTH");
            row.setFailureMessage("运营批量改期到下月后重新等待系统自动处理");
            row.setUpdatedAt(now);
            selfMediaPublishScheduleMapper.updateById(row);
            scheduleService.syncArticleForScheduleState(row);
            changed++;
        }
        if (changed <= 0) {
            throw new BizException(ERROR_CODE, "当前批次没有可改期的异常排期");
        }
        return getBatchDetail(project.getId(), targetMonth);
    }

    @Transactional
    public ProjectSelfMediaScheduleBatchDetailVO ignoreAbnormalScheduleItems(Long projectId, String targetMonth) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        parseTargetMonth(targetMonth);
        ProjectSelfMediaScheduleBatchDetailVO detail = getBatchDetail(projectId, targetMonth);
        if (detail == null || detail.getItems() == null || detail.getItems().isEmpty()) {
            throw new BizException(ERROR_CODE, "自动排期批次不存在或没有可处理明细");
        }
        List<ProjectSelfMediaScheduleBatchDetailVO.Item> candidates = detail.getItems().stream()
                .filter(item -> item.getScheduleId() != null && isRetryableScheduleStatus(item.getScheduleStatus()))
                .toList();
        rejectPublishResultCheckBatchAction(
                candidates,
                ERROR_PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN,
                "发布结果未知的排期不得批量忽略，请先重新校验发布结果"
        );
        LocalDateTime now = LocalDateTime.now(clock);
        int ignored = 0;
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : candidates) {
            SelfMediaPublishSchedule row = selfMediaPublishScheduleMapper.selectByIdForUpdate(item.getScheduleId());
            if (row == null || !isRetryableScheduleStatus(row.getStatus())) {
                continue;
            }
            rejectPublishResultCheckBatchAction(
                    row,
                    ERROR_PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN,
                    "发布结果未知的排期不得批量忽略，请先重新校验发布结果"
            );
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_CANCELLED);
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
            row.setLockedUntil(null);
            row.setNextAttemptAt(null);
            row.setCancelledAt(now);
            row.setFailureCode("IGNORED_BY_OPERATOR");
            row.setFailureMessage("运营批量忽略该异常排期");
            row.setUpdatedAt(now);
            selfMediaPublishScheduleMapper.updateById(row);
            scheduleService.syncArticleForScheduleState(row);
            ignored++;
        }
        if (ignored <= 0) {
            throw new BizException(ERROR_CODE, "当前批次没有可忽略的异常排期");
        }
        return getBatchDetail(project.getId(), targetMonth);
    }

    public SelfMediaPublishAutoScheduleResponse previewForProject(Long projectId,
                                                                  ProjectSelfMediaAutoScheduleRequest request) {
        Project project = requireProject(projectId);
        requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(projectId);
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        YearMonth targetMonth = parseTargetMonth(autoRequest.getTargetMonth());
        ensureProjectSchedulable(project, targetMonth);
        List<Long> accountIds = autoRequest.getSelfMediaAccountIds().isEmpty()
                ? selectBrandAccountIds(project.getBrandId())
                : autoRequest.getSelfMediaAccountIds();
        AccountPublishPlanResult planResult = buildAccountPublishPlans(project, autoRequest.getTargetMonth(), accountIds);
        List<AccountPublishPlan> plans = planResult.plans();
        SelfMediaPublishAutoScheduleResponse response = evaluateCapacity(autoRequest, plans);
        applyPlanBuildWarnings(response, planResult);
        response.getPlannedItems().addAll(buildPreviewItems(response.getSlotGroups(), plans));
        response.setRejectedCount((int) response.getPlannedItems().stream()
                .filter(item -> "rejected".equals(item.getStatus()))
                .count());
        return response;
    }

    @Transactional
    public SelfMediaPublishAutoScheduleResponse createForProject(Long projectId,
                                                                 ProjectSelfMediaAutoScheduleRequest request,
                                                                 String triggerMode) {
        return createForProject(projectId, request, triggerMode, null);
    }

    @Transactional
    public SelfMediaPublishAutoScheduleResponse createForProject(Long projectId,
                                                                 ProjectSelfMediaAutoScheduleRequest request,
                                                                 String triggerMode,
                                                                 String idempotencyKey) {
        Project project = requireProject(projectId);
        SysUser operator = requireProjectOperate(project);
        ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(projectId);
        if (config == null || !Boolean.TRUE.equals(config.getAutoScheduleEnabled())) {
            throw new BizException(ERROR_CODE, "项目未开启自媒体自动排期开关");
        }
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        YearMonth targetMonth = parseTargetMonth(autoRequest.getTargetMonth());
        ensureProjectSchedulable(project, targetMonth);
        ProjectSelfMediaScheduleBatch existed = batchMapper.selectByProjectAndMonth(projectId, autoRequest.getTargetMonth());
        existed = settleTerminalGenerationFailure(existed);
        if (isActiveOrCompletedBatch(existed)) {
            if (sameIdempotencyKey(existed, idempotencyKey)) {
                return acceptedExistingBatchResponse(autoRequest, existed);
            }
            if (Boolean.TRUE.equals(request.getSupplementExistingBatch())) {
                if (STATUS_PROCESSING.equals(normalizeText(existed.getStatus()))) {
                    throw new BizException(ERROR_CODE, "该项目本月自动化排期正在处理中，请等待完成后再补排");
                }
                return supplementGenerationBatch(project, config, request, triggerMode, operator.getId(), existed, idempotencyKey);
            }
            throw new BizException(ERROR_CODE, "该项目本月已创建过自动化排期，不能重复创建");
        }
        if (isFailedBatchWithGenerationPlan(existed)) {
            throw new BizException(ERROR_CODE, "该项目本月已有失败批次且已生成文章计划，请进入批次明细使用补排期/重试处理，避免重复生成文章");
        }
        return acceptGenerationBatch(project, config, request, triggerMode, operator.getId(), existed, idempotencyKey);
    }

    private SelfMediaPublishAutoScheduleResponse supplementGenerationBatch(Project project,
                                                                           ProjectSelfMediaScheduleConfig config,
                                                                           ProjectSelfMediaAutoScheduleRequest request,
                                                                           String triggerMode,
                                                                           Long operatorId,
                                                                           ProjectSelfMediaScheduleBatch batch,
                                                                           String idempotencyKey) {
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        List<Long> accountIds = autoRequest.getSelfMediaAccountIds().isEmpty()
                ? selectBrandAccountIds(project.getBrandId())
                : autoRequest.getSelfMediaAccountIds();
        AccountPublishPlanResult planResult = buildAccountPublishPlans(project, autoRequest.getTargetMonth(), accountIds);
        List<AccountPublishPlan> plans = planResult.plans();
        if (plans.isEmpty()) {
            throw new BizException(ERROR_CODE, "该项目本月没有可补排的自媒体账号或剩余额度");
        }
        SelfMediaPublishAutoScheduleResponse capacity = evaluateCapacity(autoRequest, plans);
        applyPlanBuildWarnings(capacity, planResult);
        if (!Boolean.TRUE.equals(capacity.getEnough())) {
            String decisionStrategy = normalizeText(request.getDecisionStrategy());
            if (!STRATEGY_CARRY_OVER.equals(decisionStrategy)) {
                throwCapacityInsufficient(capacity);
            }
            String decisionReason = trimToNull(request.getDecisionReason());
            if (!StringUtils.hasText(decisionReason)) {
                throw new BizException(
                        ERROR_CODE,
                        "结转补排必须填写决策原因",
                        400,
                        Map.of("errorCode", ERROR_DECISION_REASON_REQUIRED)
                );
            }
            currentUserService.ensurePermission(PERM_SELF_MEDIA_SCHEDULE_LATE_START_DECIDE);
            CarryOverSplit split = splitPlansByCapacity(plans, capacity);
            CarryOverResult result = createCarryOver(
                    project,
                    batch.getId(),
                    autoRequest.getTargetMonth(),
                    capacity,
                    split.carryOverPlans(),
                    operatorId,
                    decisionReason
            );
            if (split.currentMonthPlans().isEmpty()) {
                SelfMediaPublishAutoScheduleResponse response = acceptedResponse(autoRequest, 0);
                response.setCreated(false);
                response.setDecisionStrategy(STRATEGY_CARRY_OVER);
                applyCapacityFields(response, capacity);
                applyCarryOverResult(response, result);
                return response;
            }
            return appendGenerationPlansToBatch(
                    project,
                    autoRequest,
                    triggerMode,
                    operatorId,
                    batch,
                    idempotencyKey,
                    split.currentMonthPlans(),
                    capacity,
                    result
            );
        }
        return appendGenerationPlansToBatch(project, autoRequest, triggerMode, operatorId, batch, idempotencyKey, plans, capacity, null);
    }

    public int createDueEnabledProjects(String targetMonth, int limit) {
        YearMonth parsedTargetMonth = parseTargetMonth(targetMonth);
        int processed = 0;
        int pageSize = Math.max(1, limit);
        Long lastProjectId = 0L;
        while (true) {
            List<ProjectSelfMediaScheduleConfig> configs = configMapper.selectEnabledPage(lastProjectId, pageSize);
            if (configs.isEmpty()) {
                break;
            }
            for (ProjectSelfMediaScheduleConfig config : configs) {
                lastProjectId = config.getProjectId();
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
                    Long operatorId = resolveSystemOperatorId(project, config);
                    if (isFailedBatchWithGenerationPlan(existed)) {
                        recordSkippedBatch(project, config, targetMonth, "本月已有失败批次且已生成文章计划，请人工进入批次明细补排期/重试", operatorId, existed);
                        continue;
                    }
                    String projectBlockReason = projectAutoScheduleBlockReason(project, parsedTargetMonth);
                    if (projectBlockReason != null) {
                        recordSkippedBatch(project, config, targetMonth, projectBlockReason, operatorId, existed);
                        continue;
                    }
                    List<Long> accountIds = selectBrandAccountIds(project.getBrandId());
                    if (accountIds.isEmpty()) {
                        recordSkippedBatch(project, config, targetMonth, "项目品牌暂无可用于自媒体自动排期的有效账号", operatorId, existed);
                        continue;
                    }
                    ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
                    request.setSelfMediaAccountIds(accountIds);
                    request.setTargetMonth(targetMonth);
                    request.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
                    request.setIncludeAdjustedWorkdays(config.getIncludeAdjustedWorkdays());
                    acceptGenerationBatch(project, config, request, TRIGGER_JOB, operatorId, existed, null);
                    processed++;
                } catch (Exception ex) {
                    log.warn("project self-media auto schedule skipped projectId={} month={} error={}",
                            config.getProjectId(), targetMonth, trimMessage(ex.getMessage()));
                }
            }
        }
        return processed;
    }

    public int createDueEnabledProjectPlans(String targetMonth,
                                            String triggerMode,
                                            LocalDateTime windowStart,
                                            int windowHours,
                                            int jitterMinutes,
                                            int pageSize) {
        parseTargetMonth(targetMonth);
        List<ProjectSelfMediaScheduleConfig> configs = new ArrayList<>();
        Long lastProjectId = 0L;
        int safePageSize = Math.max(1, pageSize);
        while (true) {
            List<ProjectSelfMediaScheduleConfig> page = configMapper.selectEnabledPage(lastProjectId, safePageSize);
            if (page.isEmpty()) {
                break;
            }
            configs.addAll(page);
            lastProjectId = page.get(page.size() - 1).getProjectId();
        }
        if (configs.isEmpty()) {
            return 0;
        }
        List<LocalDateTime> executeTimes = spreadMonthlySchedulePlanTimes(
                windowStart == null ? LocalDateTime.now(clock) : windowStart,
                configs.size(),
                windowHours,
                jitterMinutes
        );
        int created = 0;
        for (int i = 0; i < configs.size(); i++) {
            ProjectSelfMediaScheduleConfig config = configs.get(i);
            Project project = projectMapper.selectById(config.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                continue;
            }
            ProjectSelfMediaSchedulePlan plan = new ProjectSelfMediaSchedulePlan();
            plan.setProjectId(project.getId());
            plan.setCompanyId(project.getCompanyId());
            plan.setBrandId(project.getBrandId());
            plan.setTargetMonth(targetMonth);
            plan.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode.trim() : TRIGGER_JOB);
            plan.setPlannedExecuteAt(executeTimes.get(i));
            plan.setNextAttemptAt(executeTimes.get(i));
            plan.setStatus("pending");
            plan.setRetryCount(0);
            try {
                plan.setCreatedBy(resolveSystemOperatorId(project, config));
                planMapper.insert(plan);
                created++;
            } catch (DuplicateKeyException ignored) {
                if (resetExistingMonthlyPlanForCompensation(plan)) {
                    created++;
                }
            } catch (Exception ex) {
                log.warn("project self-media schedule stagger plan create failed projectId={} month={} error={}",
                        project.getId(), targetMonth, trimMessage(ex.getMessage()));
            }
        }
        return created;
    }

    private boolean resetExistingMonthlyPlanForCompensation(ProjectSelfMediaSchedulePlan plan) {
        if (plan == null || !"compensation".equals(plan.getTriggerMode())) {
            return false;
        }
        ProjectSelfMediaSchedulePlan existing = planMapper.selectOne(
                new LambdaQueryWrapper<ProjectSelfMediaSchedulePlan>()
                        .eq(ProjectSelfMediaSchedulePlan::getProjectId, plan.getProjectId())
                        .eq(ProjectSelfMediaSchedulePlan::getTargetMonth, plan.getTargetMonth())
                        .last("LIMIT 1")
        );
        if (existing == null || !List.of("failed", "failed_terminal", "skipped").contains(existing.getStatus())) {
            return false;
        }
        int updated = planMapper.update(null, new LambdaUpdateWrapper<ProjectSelfMediaSchedulePlan>()
                .eq(ProjectSelfMediaSchedulePlan::getId, existing.getId())
                .in(ProjectSelfMediaSchedulePlan::getStatus, List.of("failed", "failed_terminal", "skipped"))
                .set(ProjectSelfMediaSchedulePlan::getTriggerMode, plan.getTriggerMode())
                .set(ProjectSelfMediaSchedulePlan::getPlannedExecuteAt, plan.getPlannedExecuteAt())
                .set(ProjectSelfMediaSchedulePlan::getNextAttemptAt, plan.getNextAttemptAt())
                .set(ProjectSelfMediaSchedulePlan::getStatus, "pending")
                .set(ProjectSelfMediaSchedulePlan::getRetryCount, 0)
                .set(ProjectSelfMediaSchedulePlan::getFailureMessage, null)
                .set(ProjectSelfMediaSchedulePlan::getCreatedBy, plan.getCreatedBy()));
        return updated > 0;
    }

    public int processDueProjectSchedulePlans(int limit, int retryMaxCount) {
        return processDueProjectSchedulePlans(limit, retryMaxCount, 120);
    }

    public int processDueProjectSchedulePlans(int limit, int retryMaxCount, int runningTimeoutMinutes) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        int safeRetryMax = Math.max(1, retryMaxCount);
        recoverStaleRunningProjectSchedulePlans(safeRetryMax, runningTimeoutMinutes);
        List<ProjectSelfMediaSchedulePlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<ProjectSelfMediaSchedulePlan>()
                        .in(ProjectSelfMediaSchedulePlan::getStatus, List.of("pending", "failed"))
                        .le(ProjectSelfMediaSchedulePlan::getPlannedExecuteAt, now)
                        .le(ProjectSelfMediaSchedulePlan::getNextAttemptAt, now)
                        .lt(ProjectSelfMediaSchedulePlan::getRetryCount, safeRetryMax)
                        .orderByAsc(ProjectSelfMediaSchedulePlan::getPlannedExecuteAt, ProjectSelfMediaSchedulePlan::getId)
                        .last("LIMIT " + Math.max(1, limit))
        );
        int processed = 0;
        for (ProjectSelfMediaSchedulePlan plan : plans) {
            String status = plan.getStatus();
            int claimed = planMapper.update(null, new LambdaUpdateWrapper<ProjectSelfMediaSchedulePlan>()
                    .eq(ProjectSelfMediaSchedulePlan::getId, plan.getId())
                    .eq(ProjectSelfMediaSchedulePlan::getStatus, status)
                    .set(ProjectSelfMediaSchedulePlan::getStatus, "running")
                    .set(ProjectSelfMediaSchedulePlan::getFailureMessage, null));
            if (claimed <= 0) {
                continue;
            }
            runProjectSchedulePlan(plan, safeRetryMax);
            processed++;
        }
        return processed;
    }

    public int recoverStaleRunningProjectSchedulePlans(int retryMaxCount, int runningTimeoutMinutes) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        LocalDateTime deadline = now.minusMinutes(Math.max(1, runningTimeoutMinutes));
        int safeRetryMax = Math.max(1, retryMaxCount);
        List<ProjectSelfMediaSchedulePlan> stalePlans = planMapper.selectList(
                new QueryWrapper<ProjectSelfMediaSchedulePlan>()
                        .eq("status", "running")
                        .le("updated_at", deadline)
                        .orderByAsc("updated_at", "id")
        );
        int recovered = 0;
        for (ProjectSelfMediaSchedulePlan plan : stalePlans) {
            int nextRetryCount = (plan.getRetryCount() == null ? 0 : plan.getRetryCount()) + 1;
            boolean exhausted = nextRetryCount >= safeRetryMax;
            int updated = planMapper.update(null, new UpdateWrapper<ProjectSelfMediaSchedulePlan>()
                    .eq("id", plan.getId())
                    .eq("status", "running")
                    .set("status", exhausted ? "failed_terminal" : "failed")
                    .set("retry_count", nextRetryCount)
                    .set("next_attempt_at", exhausted ? null : now)
                    .set("failure_message",
                            exhausted
                                    ? "排期计划运行超时且已达到最大重试次数，请人工确认"
                                    : "排期计划运行超时，已释放为可重试状态"));
            recovered += updated > 0 ? 1 : 0;
        }
        if (recovered > 0) {
            log.warn("project self-media stale running schedule plans recovered count={} timeoutMinutes={}",
                    recovered, Math.max(1, runningTimeoutMinutes));
        }
        return recovered;
    }

    private void runProjectSchedulePlan(ProjectSelfMediaSchedulePlan plan, int retryMaxCount) {
        try {
            YearMonth parsedTargetMonth = parseTargetMonth(plan.getTargetMonth());
            ProjectSelfMediaScheduleConfig config = configMapper.selectByProjectId(plan.getProjectId());
            if (config == null || !Boolean.TRUE.equals(config.getAutoScheduleEnabled())) {
                markProjectSchedulePlanTerminal(plan, "skipped", "项目未开启自媒体自动排期开关");
                return;
            }
            ProjectSelfMediaScheduleBatch existed = settleTerminalGenerationFailure(
                    batchMapper.selectByProjectAndMonth(plan.getProjectId(), plan.getTargetMonth()));
            if (isActiveOrCompletedBatch(existed)) {
                markProjectSchedulePlanTerminal(plan, "completed", null);
                return;
            }
            Project project = projectMapper.selectById(plan.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                markProjectSchedulePlanTerminal(plan, "skipped", "项目不存在或已删除");
                return;
            }
            Long operatorId = plan.getCreatedBy() != null && plan.getCreatedBy() > 0
                    ? plan.getCreatedBy()
                    : resolveSystemOperatorId(project, config);
            if (isFailedBatchWithGenerationPlan(existed)) {
                recordSkippedBatch(project, config, plan.getTargetMonth(), "本月已有失败批次且已生成文章计划，请人工进入批次明细补排期/重试", operatorId, existed);
                markProjectSchedulePlanTerminal(plan, "skipped", "本月已有失败批次且已生成文章计划");
                return;
            }
            String projectBlockReason = projectAutoScheduleBlockReason(project, parsedTargetMonth);
            if (projectBlockReason != null) {
                recordSkippedBatch(project, config, plan.getTargetMonth(), projectBlockReason, operatorId, existed);
                markProjectSchedulePlanTerminal(plan, "skipped", projectBlockReason);
                return;
            }
            List<Long> accountIds = selectBrandAccountIds(project.getBrandId());
            if (accountIds.isEmpty()) {
                recordSkippedBatch(project, config, plan.getTargetMonth(), "项目品牌暂无可用于自媒体自动排期的有效账号", operatorId, existed);
                markProjectSchedulePlanTerminal(plan, "skipped", "项目品牌暂无可用于自媒体自动排期的有效账号");
                return;
            }
            ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
            request.setSelfMediaAccountIds(accountIds);
            request.setTargetMonth(plan.getTargetMonth());
            request.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
            request.setIncludeAdjustedWorkdays(config.getIncludeAdjustedWorkdays());
            acceptGenerationBatch(project, config, request, plan.getTriggerMode(), operatorId, existed, null);
            markProjectSchedulePlanTerminal(plan, "completed", null);
        } catch (Exception ex) {
            int retryCount = (plan.getRetryCount() == null ? 0 : plan.getRetryCount()) + 1;
            boolean exhausted = retryCount >= retryMaxCount;
            planMapper.update(null, new LambdaUpdateWrapper<ProjectSelfMediaSchedulePlan>()
                    .eq(ProjectSelfMediaSchedulePlan::getId, plan.getId())
                    .set(ProjectSelfMediaSchedulePlan::getStatus, exhausted ? "failed_terminal" : "failed")
                    .set(ProjectSelfMediaSchedulePlan::getRetryCount, retryCount)
                    .set(ProjectSelfMediaSchedulePlan::getNextAttemptAt, LocalDateTime.now(clock).plusMinutes(15))
                    .set(ProjectSelfMediaSchedulePlan::getFailureMessage, trimMessage(ex.getMessage())));
        }
    }

    private void markProjectSchedulePlanTerminal(ProjectSelfMediaSchedulePlan plan, String status, String message) {
        planMapper.update(null, new LambdaUpdateWrapper<ProjectSelfMediaSchedulePlan>()
                .eq(ProjectSelfMediaSchedulePlan::getId, plan.getId())
                .set(ProjectSelfMediaSchedulePlan::getStatus, status)
                .set(ProjectSelfMediaSchedulePlan::getFailureMessage, StringUtils.hasText(message) ? trimMessage(message) : null));
    }

    private List<LocalDateTime> spreadMonthlySchedulePlanTimes(LocalDateTime windowStart,
                                                              int count,
                                                              int windowHours,
                                                              int jitterMinutes) {
        if (count <= 0) {
            return List.of();
        }
        LocalDateTime base = (windowStart == null ? LocalDateTime.now(clock) : windowStart).withNano(0);
        long windowSeconds = TimeUnit.HOURS.toSeconds(Math.max(1, windowHours));
        long jitterSeconds = Math.max(0, jitterMinutes) * 60L;
        List<LocalDateTime> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offsetSeconds = count <= 1 ? 0L : (windowSeconds * i) / count;
            if (jitterSeconds > 0) {
                offsetSeconds += ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
            }
            offsetSeconds = Math.min(offsetSeconds, windowSeconds);
            times.add(base.plusSeconds(offsetSeconds).withNano(0));
        }
        return times;
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

    public int compensateRetryableAbnormalSchedules(int limit) {
        int processed = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        List<SelfMediaPublishSchedule> candidates = selfMediaPublishScheduleMapper.selectProjectAutoCompensationCandidates(
                List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
                        SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED
                ),
                now,
                Math.max(1, limit)
        );
        for (SelfMediaPublishSchedule candidate : candidates) {
            if (candidate == null || !isAutoCompensationCandidate(candidate)) {
                continue;
            }
            try {
                scheduleService.retryNowSystem(candidate.getId(), "项目自动排期补偿任务已重新计算安全执行时间并重试");
                processed++;
            } catch (Exception ex) {
                log.warn("project self-media auto schedule compensation skipped scheduleId={} code={} error={}",
                        candidate.getId(), candidate.getFailureCode(), trimMessage(ex.getMessage()));
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

    private boolean sameIdempotencyKey(ProjectSelfMediaScheduleBatch batch, String idempotencyKey) {
        if (batch == null || !StringUtils.hasText(batch.getRequestPayload()) || !StringUtils.hasText(idempotencyKey)) {
            return false;
        }
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        return payload != null && idempotencyKey.trim().equals(payload.idempotencyKey());
    }

    private SelfMediaPublishAutoScheduleResponse acceptedExistingBatchResponse(SelfMediaPublishAutoScheduleRequest request,
                                                                              ProjectSelfMediaScheduleBatch batch) {
        SelfMediaPublishAutoScheduleResponse response = acceptedResponse(request, batch.getPlannedCount() == null ? 0 : batch.getPlannedCount());
        response.setCreated(true);
        response.setPlannedCount(batch.getPlannedCount() == null ? 0 : batch.getPlannedCount());
        response.setRejectedCount(batch.getRejectedCount() == null ? 0 : batch.getRejectedCount());
        response.setRequestedCount(batch.getRequestedCount());
        response.setDeficitCount(batch.getDeficitCount());
        response.setCarryOverCount(batch.getCarryOverCount());
        if (safeCount(batch.getCarryOverCount()) > 0) {
            response.setDecisionStrategy(STRATEGY_CARRY_OVER);
        }
        if (StringUtils.hasText(batch.getCapacitySnapshotJson())) {
            SelfMediaPublishAutoScheduleResponse capacity = readAutoScheduleResponse(batch.getCapacitySnapshotJson());
            applyCapacityFields(response, capacity);
        }
        return response;
    }

    private YearMonth parseTargetMonth(String targetMonth) {
        if (!StringUtils.hasText(targetMonth)) {
            throw new BizException(ERROR_CODE, "targetMonth is required");
        }
        try {
            return YearMonth.parse(targetMonth.trim());
        } catch (Exception ex) {
            throw new BizException(ERROR_CODE, "targetMonth must use yyyy-MM format");
        }
    }

    private void ensureProjectSchedulable(Project project, YearMonth targetMonth) {
        String reason = projectAutoScheduleBlockReason(project, targetMonth);
        if (reason != null) {
            throw new BizException(ERROR_CODE, reason);
        }
    }

    private String projectAutoScheduleBlockReason(Project project, YearMonth targetMonth) {
        if (project == null) {
            return "项目不存在";
        }
        if (!"active".equals(normalizeText(project.getStatus()))) {
            return "项目不是 active 状态，不能创建自媒体自动排期";
        }
        LocalDate monthStart = targetMonth.atDay(1);
        if (project.getEndDate() != null && project.getEndDate().isBefore(monthStart)) {
            return "项目服务期已结束，不能创建自媒体自动排期";
        }
        return null;
    }

    private boolean isFailedBatchWithGenerationPlan(ProjectSelfMediaScheduleBatch batch) {
        if (batch == null || !STATUS_FAILED.equals(normalizeText(batch.getStatus()))) {
            return false;
        }
        GenerationPayload payload = readGenerationPayload(batch.getRequestPayload());
        return payload != null && payload.plans() != null && !payload.plans().isEmpty();
    }

    private void recordSkippedBatch(Project project,
                                    ProjectSelfMediaScheduleConfig config,
                                    String targetMonth,
                                    String reason,
                                    Long operatorId,
                                    ProjectSelfMediaScheduleBatch reusableBatch) {
        ProjectSelfMediaScheduleBatch batch = reusableBatch == null
                ? new ProjectSelfMediaScheduleBatch()
                : reusableBatch;
        batch.setProjectId(project.getId());
        batch.setBrandId(project.getBrandId());
        batch.setCompanyId(project.getCompanyId());
        batch.setTargetMonth(targetMonth);
        batch.setTriggerMode(TRIGGER_JOB);
        batch.setStatus(STATUS_SKIPPED);
        batch.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
        batch.setArticleCount(0);
        batch.setAccountCount(0);
        batch.setPlannedCount(0);
        batch.setCreatedCount(0);
        batch.setRejectedCount(0);
        batch.setGenerationBatchIds(null);
        batch.setRequestPayload(toJson(Map.of(
                "targetMonth", targetMonth,
                "autoScheduleEnabled", config != null && Boolean.TRUE.equals(config.getAutoScheduleEnabled()),
                "includeAdjustedWorkdays", config != null && Boolean.TRUE.equals(config.getIncludeAdjustedWorkdays())
        )));
        batch.setResultSnapshot(null);
        batch.setFailureMessage(trimMessage(reason));
        batch.setCreatedBy(operatorId);
        batch.setUpdatedBy(operatorId);
        if (reusableBatch == null || reusableBatch.getId() == null) {
            try {
                batchMapper.insert(batch);
            } catch (DuplicateKeyException ignored) {
                ProjectSelfMediaScheduleBatch raced =
                        batchMapper.selectByProjectAndMonth(project.getId(), targetMonth);
                if (raced != null && !isActiveOrCompletedBatch(raced)) {
                    recordSkippedBatch(project, config, targetMonth, reason, operatorId, raced);
                }
            }
        } else {
            batchMapper.updateById(batch);
        }
    }

    private SelfMediaPublishAutoScheduleResponse acceptGenerationBatch(Project project,
                                                                       ProjectSelfMediaScheduleConfig config,
                                                                       ProjectSelfMediaAutoScheduleRequest request,
                                                                       String triggerMode,
                                                                       Long operatorId,
                                                                       ProjectSelfMediaScheduleBatch reusableBatch) {
        return acceptGenerationBatch(project, config, request, triggerMode, operatorId, reusableBatch, null);
    }

    private SelfMediaPublishAutoScheduleResponse acceptGenerationBatch(Project project,
                                                                       ProjectSelfMediaScheduleConfig config,
                                                                       ProjectSelfMediaAutoScheduleRequest request,
                                                                       String triggerMode,
                                                                       Long operatorId,
                                                                       ProjectSelfMediaScheduleBatch reusableBatch,
                                                                       String idempotencyKey) {
        SelfMediaPublishAutoScheduleRequest autoRequest = toAutoRequest(project, config, request);
        List<Long> accountIds = autoRequest.getSelfMediaAccountIds().isEmpty()
                ? selectBrandAccountIds(project.getBrandId())
                : autoRequest.getSelfMediaAccountIds();
        AccountPublishPlanResult planResult = buildAccountPublishPlans(project, autoRequest.getTargetMonth(), accountIds);
        List<AccountPublishPlan> plans = planResult.plans();
        if (plans.isEmpty()) {
            throw new BizException(ERROR_CODE, "该项目本月自媒体平台剩余额度为 0，无法创建自动化排期");
        }
        SelfMediaPublishAutoScheduleResponse capacity = evaluateCapacity(autoRequest, plans);
        applyPlanBuildWarnings(capacity, planResult);
        if (!Boolean.TRUE.equals(capacity.getEnough())) {
            return acceptInsufficientCapacityBatch(
                    project,
                    autoRequest,
                    request,
                    triggerMode,
                    operatorId,
                    reusableBatch,
                    idempotencyKey,
                    plans,
                    capacity
            );
        }

        return acceptGenerationBatchWithPlans(
                project,
                autoRequest,
                triggerMode,
                operatorId,
                reusableBatch,
                idempotencyKey,
                plans,
                capacity,
                null
        );
    }

    private SelfMediaPublishAutoScheduleResponse appendGenerationPlansToBatch(Project project,
                                                                              SelfMediaPublishAutoScheduleRequest autoRequest,
                                                                              String triggerMode,
                                                                              Long operatorId,
                                                                              ProjectSelfMediaScheduleBatch batch,
                                                                              String idempotencyKey,
                                                                              List<AccountPublishPlan> plans,
                                                                              SelfMediaPublishAutoScheduleResponse capacity,
                                                                              CarryOverResult carryOverResult) {
        GenerationPayload existingPayload = readGenerationPayload(batch.getRequestPayload());
        List<GenerationPlan> existingPlans = existingPayload == null || existingPayload.plans() == null
                ? List.of()
                : existingPayload.plans();
        batch.setTriggerMode(StringUtils.hasText(triggerMode) ? triggerMode : TRIGGER_MANUAL);
        batch.setScheduleStrategy(autoRequest.getScheduleStrategy());
        batch.setStatus(STATUS_PROCESSING);
        batch.setFailureMessage(null);
        batch.setUpdatedBy(operatorId);
        batch.setRequestedCount(safeCount(batch.getRequestedCount()) + plans.size());
        batch.setDeficitCount(safeCount(batch.getDeficitCount()) + (capacity == null ? 0 : safeCount(capacity.getDeficitCount())));
        batch.setCarryOverCount(safeCount(batch.getCarryOverCount()) + (carryOverResult == null ? 0 : safeCount(carryOverResult.count())));
        batch.setDecisionOperatorId(carryOverResult == null ? batch.getDecisionOperatorId() : operatorId);
        batch.setDecisionReason(carryOverResult == null ? batch.getDecisionReason() : trimMessage(carryOverResult.reason()));
        batch.setCapacitySnapshotJson(capacity == null ? batch.getCapacitySnapshotJson() : toJson(capacity));

        try {
            List<GenerationPlan> newGenerationPlans = createGenerationBatches(project, batch, plans, operatorId);
            List<GenerationPlan> mergedPlans = new ArrayList<>(existingPlans);
            mergedPlans.addAll(newGenerationPlans);
            batch.setArticleCount(mergedPlans.size());
            batch.setAccountCount((int) mergedPlans.stream().map(GenerationPlan::selfMediaAccountId).distinct().count());
            batch.setPlannedCount(mergedPlans.size());
            batch.setGenerationBatchIds(toJson(mergedPlans.stream()
                    .map(GenerationPlan::generationBatchId)
                    .distinct()
                    .toList()));
            batch.setRequestPayload(toJson(new GenerationPayload(
                    autoRequest.getTargetMonth(),
                    autoRequest.getScheduleStrategy(),
                    Boolean.TRUE.equals(autoRequest.getIncludeAdjustedWorkdays()),
                    StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null,
                    mergedPlans
            )));
            batchMapper.updateById(batch);
            consumePendingCarryOvers(plans);
            SelfMediaPublishAutoScheduleResponse response = acceptedResponse(autoRequest, newGenerationPlans.size());
            response.setCreated(true);
            response.setPlannedCount(newGenerationPlans.size());
            if (carryOverResult != null) {
                response.setDecisionStrategy(STRATEGY_CARRY_OVER);
                applyCapacityFields(response, carryOverResult.capacity());
                applyCarryOverResult(response, carryOverResult);
            }
            return response;
        } catch (RuntimeException ex) {
            batch.setStatus(STATUS_PARTIAL_FAILED);
            batch.setFailureMessage(trimMessage(ex.getMessage()));
            batchMapper.updateById(batch);
            throw ex;
        }
    }

    private SelfMediaPublishAutoScheduleResponse acceptGenerationBatchWithPlans(Project project,
                                                                                SelfMediaPublishAutoScheduleRequest autoRequest,
                                                                                String triggerMode,
                                                                                Long operatorId,
                                                                                ProjectSelfMediaScheduleBatch reusableBatch,
                                                                                String idempotencyKey,
                                                                                List<AccountPublishPlan> plans,
                                                                                SelfMediaPublishAutoScheduleResponse capacity,
                                                                                CarryOverResult carryOverResult) {
        ProjectSelfMediaScheduleBatch batch = reusableBatch == null
                ? newBatch(project, autoRequest, triggerMode, operatorId)
                : resetReusableBatch(reusableBatch, project, autoRequest, triggerMode, operatorId);
        SelfMediaPublishAutoScheduleResponse capacitySnapshot = capacity != null
                ? capacity
                : carryOverResult == null ? null : carryOverResult.capacity();
        batch.setStatus(STATUS_PROCESSING);
        batch.setArticleCount(plans.size());
        batch.setAccountCount((int) plans.stream().map(AccountPublishPlan::accountId).distinct().count());
        batch.setPlannedCount(plans.size());
        batch.setCreatedCount(0);
        batch.setRejectedCount(0);
        batch.setRequestedCount(capacitySnapshot == null ? plans.size() : safeCount(capacitySnapshot.getRequestedCount()));
        batch.setDeficitCount(capacitySnapshot == null ? 0 : safeCount(capacitySnapshot.getDeficitCount()));
        batch.setCarryOverCount(carryOverResult == null ? 0 : safeCount(carryOverResult.count()));
        batch.setDecisionOperatorId(carryOverResult == null ? null : operatorId);
        batch.setDecisionReason(carryOverResult == null ? null : trimMessage(carryOverResult.reason()));
        batch.setCapacitySnapshotJson(capacitySnapshot == null ? null : toJson(capacitySnapshot));
        batch.setGenerationBatchIds(null);
        batch.setResultSnapshot(null);
        batch.setFailureMessage(null);
        batch.setRequestPayload(toJson(Map.of(
                "targetMonth", autoRequest.getTargetMonth(),
                "scheduleStrategy", autoRequest.getScheduleStrategy(),
                "includeAdjustedWorkdays", autoRequest.getIncludeAdjustedWorkdays(),
                "idempotencyKey", StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : "",
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
                    StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null,
                    generationPlans
            )));
            batchMapper.updateById(batch);
            consumePendingCarryOvers(plans);
            CarryOverResult createdCarryOver = carryOverResult;
            if (carryOverResult != null) {
                createdCarryOver = createCarryOver(
                        project,
                        batch.getId(),
                        autoRequest.getTargetMonth(),
                        carryOverResult.capacity(),
                        carryOverResult.plans(),
                        operatorId,
                        carryOverResult.reason()
                );
            }
            SelfMediaPublishAutoScheduleResponse response = acceptedResponse(autoRequest, plans.size());
            response.setCreated(true);
            if (createdCarryOver != null) {
                applyCapacityFields(response, createdCarryOver.capacity());
                response.setPlannedCount(plans.size());
                applyCarryOverResult(response, createdCarryOver);
            }
            return response;
        } catch (RuntimeException ex) {
            batch.setStatus(STATUS_FAILED);
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
            batch.setStatus(STATUS_FAILED);
            batch.setCreatedCount(0);
            batch.setRejectedCount(failed);
            batch.setFailureMessage("自动排期文章生成全部失败");
            batchMapper.updateById(batch);
            return true;
        }
        SelfMediaPublishAutoScheduleResponse response = createSchedulesFromGenerated(batch, payload, generated);
        int created = response.getCreatedSchedules().size() + response.getExistingSchedules().size();
        int rejected = response.getRejectedItems().size() + failed;
        batch.setStatus(rejected > 0 ? STATUS_PARTIAL_FAILED : STATUS_CREATED);
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
                payload.scheduleStrategy(),
                payload.includeAdjustedWorkdays()
        );
        SelfMediaPublishAutoScheduleResponse response = acceptedResponse(
                batch.getBrandId(),
                payload.targetMonth(),
                payload.scheduleStrategy(),
                generated.size()
        );
        Set<AccountPublishDay> occupiedDays = new LinkedHashSet<>();
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
            request.setAllowSecondDailySchedule(!occupiedDays.add(new AccountPublishDay(
                    plan.plan().selfMediaAccountId(), plannedPublishAt.toLocalDate()
            )));
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
        batch.setStatus(rejected > 0 ? STATUS_PARTIAL_FAILED : STATUS_CREATED);
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

    private ProjectSelfMediaScheduleBatch reconcileBatchSummaryFromDetail(ProjectSelfMediaScheduleBatch batch,
                                                                          List<ProjectSelfMediaScheduleBatchDetailVO.Item> items) {
        if (batch == null || items == null || items.isEmpty() || STATUS_PROCESSING.equals(normalizeText(batch.getStatus()))) {
            return batch;
        }
        String currentStatus = normalizeText(batch.getStatus());
        if (!STATUS_CREATED.equals(currentStatus) && !STATUS_PARTIAL_FAILED.equals(currentStatus)) {
            return batch;
        }
        int created = 0;
        int rejected = 0;
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : items) {
            if (item.getScheduleId() != null) {
                created++;
                continue;
            }
            String generationStatus = normalizeText(item.getGenerationStatus());
            String scheduleStatus = normalizeText(item.getScheduleStatus());
            if ("failed".equals(generationStatus)
                    || "rejected".equals(scheduleStatus)
                    || item.getArticleId() != null) {
                rejected++;
            }
        }
        String status = rejected > 0 ? STATUS_PARTIAL_FAILED : STATUS_CREATED;
        String failureMessage = rejected > 0 ? "部分文章生成或排期失败" : null;
        boolean changed = !status.equals(normalizeText(batch.getStatus()))
                || !Objects.equals(batch.getCreatedCount(), created)
                || !Objects.equals(batch.getRejectedCount(), rejected)
                || !Objects.equals(batch.getFailureMessage(), failureMessage);
        if (!changed) {
            return batch;
        }
        batch.setStatus(status);
        batch.setCreatedCount(created);
        batch.setRejectedCount(rejected);
        batch.setFailureMessage(failureMessage);
        batchMapper.updateById(batch);
        return batch;
    }

    private boolean isGeneratedWithoutSchedule(ProjectSelfMediaScheduleBatch batch,
                                               GenerationPlan plan,
                                               BatchArticleGenerationTask task) {
        return task != null
                && "success".equals(task.getStatus())
                && task.getArticleId() != null
                && findScheduleForGenerationPlan(batch, plan) == null;
    }

    private List<BusinessCalendarService.PublishSlot> selectSlotsEvenlyByPlatform(YearMonth targetMonth,
                                                                                  List<GeneratedSchedulePlan> generated,
                                                                                  String requestedScheduleStrategy,
                                                                                  boolean includeAdjustedWorkdays) {
        List<AccountPublishPlan> plans = generated.stream()
                .map(item -> new AccountPublishPlan(item.plan().selfMediaAccountId(), item.plan().platform()))
                .toList();
        List<SelfMediaPublishAutoScheduleResponse.SlotGroup> groups = analyzeAvailableSlots(
                targetMonth,
                plans,
                requestedScheduleStrategy,
                includeAdjustedWorkdays
        );
        for (SelfMediaPublishAutoScheduleResponse.SlotGroup group : groups) {
            if (!Boolean.TRUE.equals(group.getEnough())) {
                throw new BizException(
                        ERROR_CODE,
                        firstText(group.getMessage(),
                                group.getPlatformLabel() + "目标月份剩余可自动排期时间不足，请选择其他月份或减少排期数量")
                );
            }
        }
        return selectedSlotsByGeneratedPlans(generated, groups);
    }

    private List<BusinessCalendarService.PublishSlot> selectedSlotsByGeneratedPlans(
            List<GeneratedSchedulePlan> generated,
            List<SelfMediaPublishAutoScheduleResponse.SlotGroup> groups
    ) {
        Map<String, List<SelfMediaPublishAutoScheduleResponse.SlotPreview>> previewsByPlatform = new LinkedHashMap<>();
        for (SelfMediaPublishAutoScheduleResponse.SlotGroup group : groups) {
            previewsByPlatform.put(firstText(normalizePlatform(group.getPlatform()), "unknown"), group.getSelectedSlots());
        }
        Map<String, Integer> cursorByPlatform = new LinkedHashMap<>();
        List<BusinessCalendarService.PublishSlot> result = new ArrayList<>();
        for (GeneratedSchedulePlan item : generated) {
            String platform = firstText(normalizePlatform(item.plan().platform()), "unknown");
            int cursor = cursorByPlatform.getOrDefault(platform, 0);
            List<SelfMediaPublishAutoScheduleResponse.SlotPreview> previews = previewsByPlatform.get(platform);
            if (previews == null || cursor >= previews.size()) {
                throw new BizException(ERROR_CODE, platformLabel(platform) + "目标月份剩余可自动排期时间不足，请选择其他月份或减少排期数量");
            }
            SelfMediaPublishAutoScheduleResponse.SlotPreview preview = previews.get(cursor);
            cursorByPlatform.put(platform, cursor + 1);
            result.add(new BusinessCalendarService.PublishSlot(
                    preview.getExecutionAt().toLocalDate(),
                    preview.getWindowName(),
                    preview.getExecutionAt().toLocalTime(),
                    preview.getExecutionAt().toLocalTime(),
                    preview.getExecutionAt(),
                    0,
                    null,
                    0,
                    false
            ));
        }
        return result;
    }

    private List<SelfMediaPublishAutoScheduleResponse.SlotGroup> analyzeAvailableSlots(
            YearMonth targetMonth,
            List<AccountPublishPlan> plans,
            String requestedScheduleStrategy,
            boolean includeAdjustedWorkdays
    ) {
        Map<String, List<AccountPublishPlan>> plansByPlatform = new LinkedHashMap<>();
        List<AccountPublishPlan> orderedPlans = plans.stream()
                .sorted(Comparator.comparing((AccountPublishPlan plan) -> plan.carryOverId() == null))
                .toList();
        for (AccountPublishPlan plan : orderedPlans) {
            String platform = firstText(normalizePlatform(plan.platform()), "unknown");
            plansByPlatform.computeIfAbsent(platform, ignored -> new ArrayList<>()).add(plan);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<BusinessCalendarService.PublishSlot> candidateSlots =
                businessCalendarService.allPublishSlots(targetMonth, includeAdjustedWorkdays);
        List<SelfMediaPublishAutoScheduleResponse.SlotGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<AccountPublishPlan>> entry : plansByPlatform.entrySet()) {
            String platform = entry.getKey();
            String scheduleStrategy = resolveItemStrategy(requestedScheduleStrategy, platform);
            List<BusinessCalendarService.PublishSlot> platformSlots = candidateSlots.stream()
                    .filter(slot -> isFutureExecutableSlot(slot, platform, scheduleStrategy, now))
                    .sorted(Comparator.comparing(BusinessCalendarService.PublishSlot::plannedAt))
                    .toList();
            int requestedCount = entry.getValue().size();
            int availableSlotCount = platformSlots.size();
            SelfMediaPublishAutoScheduleResponse.SlotGroup group = new SelfMediaPublishAutoScheduleResponse.SlotGroup();
            group.setPlatform(platform);
            group.setPlatformLabel(platformLabel(platform));
            group.setScheduleStrategy(scheduleStrategy);
            group.setRequestedCount(requestedCount);
            group.setAvailableSlotCount(availableSlotCount);
            group.setDeficitCount(Math.max(0, requestedCount - availableSlotCount));
            group.setRemainingWorkdayCount((int) platformSlots.stream()
                    .map(BusinessCalendarService.PublishSlot::date)
                    .distinct()
                    .count());
            group.setEnough(availableSlotCount >= requestedCount);
            group.setMessage(Boolean.TRUE.equals(group.getEnough())
                    ? "可满足目标月份自动排期"
                    : platformLabel(platform) + "目标月份剩余可自动排期时间不足，请选择其他月份或减少排期数量");
            List<BusinessCalendarService.PublishSlot> selectedSlots = group.getEnough()
                    ? selectEvenlyFromSlots(platformSlots, requestedCount)
                    : List.of();
            for (BusinessCalendarService.PublishSlot slot : selectedSlots) {
                SelfMediaPublishAutoScheduleResponse.SlotPreview preview = new SelfMediaPublishAutoScheduleResponse.SlotPreview();
                preview.setExecutionAt(slot.plannedAt());
                preview.setPlannedPublishAt(resolvePlannedPublishAt(slot, platform, scheduleStrategy));
                preview.setWindowName(slot.windowName());
                group.getSelectedSlots().add(preview);
            }
            groups.add(group);
        }
        return groups;
    }

    private SelfMediaPublishAutoScheduleResponse evaluateCapacity(SelfMediaPublishAutoScheduleRequest request,
                                                                  List<AccountPublishPlan> plans) {
        List<SelfMediaPublishAutoScheduleResponse.SlotGroup> groups = analyzeAvailableSlots(
                YearMonth.parse(request.getTargetMonth()),
                plans,
                request.getScheduleStrategy(),
                Boolean.TRUE.equals(request.getIncludeAdjustedWorkdays())
        );
        SelfMediaPublishAutoScheduleResponse summary = acceptedResponse(request, plans.size());
        summary.setSlotGroups(groups);
        applyCapacitySummary(summary);
        return summary;
    }

    private void validateAvailableSlotsBeforeGeneration(SelfMediaPublishAutoScheduleRequest request,
                                                        List<AccountPublishPlan> plans) {
        SelfMediaPublishAutoScheduleResponse summary = evaluateCapacity(request, plans);
        if (!Boolean.TRUE.equals(summary.getEnough())) {
            throwCapacityInsufficient(summary);
        }
    }

    private SelfMediaPublishAutoScheduleResponse acceptInsufficientCapacityBatch(
            Project project,
            SelfMediaPublishAutoScheduleRequest autoRequest,
            ProjectSelfMediaAutoScheduleRequest request,
            String triggerMode,
            Long operatorId,
            ProjectSelfMediaScheduleBatch reusableBatch,
            String idempotencyKey,
            List<AccountPublishPlan> plans,
            SelfMediaPublishAutoScheduleResponse capacity
    ) {
        String decisionStrategy = normalizeText(request == null ? null : request.getDecisionStrategy());
        if (STRATEGY_COMPRESSED_CURRENT_MONTH.equals(decisionStrategy)) {
            throw new BizException(
                    ERROR_CODE,
                    "压缩本月排期暂未开放，请选择结转补排或减少排期数量",
                    400,
                    Map.of("errorCode", ERROR_COMPRESS_NOT_SUPPORTED)
            );
        }
        if (!STRATEGY_CARRY_OVER.equals(decisionStrategy)) {
            throwCapacityInsufficient(capacity);
        }
        String decisionReason = trimToNull(request == null ? null : request.getDecisionReason());
        if (!StringUtils.hasText(decisionReason)) {
            throw new BizException(
                    ERROR_CODE,
                    "结转补排必须填写决策原因",
                    400,
                    Map.of("errorCode", ERROR_DECISION_REASON_REQUIRED)
            );
        }
        currentUserService.ensurePermission(PERM_SELF_MEDIA_SCHEDULE_LATE_START_DECIDE);
        CarryOverSplit split = splitPlansByCapacity(plans, capacity);
        if (split.currentMonthPlans().isEmpty()) {
            CarryOverResult result = createCarryOver(
                    project,
                    null,
                    autoRequest.getTargetMonth(),
                    capacity,
                    split.carryOverPlans(),
                    operatorId,
                    decisionReason
            );
            SelfMediaPublishAutoScheduleResponse response = acceptedResponse(autoRequest, 0);
            response.setCreated(false);
            response.setDecisionStrategy(STRATEGY_CARRY_OVER);
            applyCapacityFields(response, capacity);
            applyCarryOverResult(response, result);
            return response;
        }
        CarryOverResult result = new CarryOverResult(
                null,
                split.carryOverPlans().size(),
                YearMonth.parse(autoRequest.getTargetMonth()).plusMonths(1).toString(),
                capacity,
                split.carryOverPlans(),
                decisionReason
        );
        return acceptGenerationBatchWithPlans(
                project,
                autoRequest,
                triggerMode,
                operatorId,
                reusableBatch,
                idempotencyKey,
                split.currentMonthPlans(),
                capacity,
                result
        );
    }

    private CarryOverSplit splitPlansByCapacity(List<AccountPublishPlan> plans,
                                                SelfMediaPublishAutoScheduleResponse capacity) {
        Map<String, Integer> remainingByPlatform = new LinkedHashMap<>();
        for (SelfMediaPublishAutoScheduleResponse.SlotGroup group : capacity.getSlotGroups()) {
            String platform = firstText(normalizePlatform(group.getPlatform()), "unknown");
            int requested = safeCount(group.getRequestedCount());
            int available = safeCount(group.getAvailableSlotCount());
            remainingByPlatform.put(platform, Math.min(requested, available));
        }
        List<AccountPublishPlan> current = new ArrayList<>();
        List<AccountPublishPlan> carryOver = new ArrayList<>();
        List<AccountPublishPlan> orderedPlans = plans.stream()
                .sorted(Comparator.comparing((AccountPublishPlan plan) -> plan.carryOverId() == null))
                .toList();
        for (AccountPublishPlan plan : orderedPlans) {
            String platform = firstText(normalizePlatform(plan.platform()), "unknown");
            int remaining = remainingByPlatform.getOrDefault(platform, 0);
            if (remaining > 0) {
                current.add(plan);
                remainingByPlatform.put(platform, remaining - 1);
            } else {
                carryOver.add(plan);
            }
        }
        return new CarryOverSplit(current, carryOver);
    }

    private CarryOverResult createCarryOver(Project project,
                                            Long sourceBatchId,
                                            String sourceMonth,
                                            SelfMediaPublishAutoScheduleResponse capacity,
                                            List<AccountPublishPlan> carryOverPlans,
                                            Long operatorId,
                                            String decisionReason) {
        if (carryOverPlans == null || carryOverPlans.isEmpty()) {
            return null;
        }
        String targetMonth = YearMonth.parse(sourceMonth).plusMonths(1).toString();
        lockCarryOverDecisionScope(project.getId());
        ProjectSelfMediaScheduleCarryOver existed = findPendingCarryOver(project.getId(), sourceMonth, targetMonth);
        if (existed != null) {
            return new CarryOverResult(
                    existed.getId(),
                    safeCount(existed.getCarryOverCount()),
                    existed.getTargetMonth(),
                    capacity,
                    carryOverPlans,
                    decisionReason
            );
        }
        ProjectSelfMediaScheduleCarryOver row = new ProjectSelfMediaScheduleCarryOver();
        row.setProjectId(project.getId());
        row.setCompanyId(project.getCompanyId());
        row.setBrandId(project.getBrandId());
        row.setSourceBatchId(sourceBatchId);
        row.setSourceMonth(sourceMonth);
        row.setTargetMonth(targetMonth);
        row.setRequestedCount(safeCount(capacity.getRequestedCount()));
        row.setCarryOverCount(carryOverPlans.size());
        row.setConsumedCount(0);
        row.setStatus(CARRY_OVER_STATUS_PENDING);
        row.setDecisionOperatorId(operatorId);
        row.setDecisionReason(trimMessage(decisionReason));
        row.setCapacitySnapshotJson(toJson(capacity));
        row.setCarryOverPlanJson(toJson(carryOverPlans.stream()
                .map(plan -> new CarryOverPlan(plan.accountId(), plan.platform()))
                .toList()));
        carryOverMapper.insert(row);
        return new CarryOverResult(
                row.getId(),
                carryOverPlans.size(),
                targetMonth,
                capacity,
                carryOverPlans,
                decisionReason
        );
    }

    private void lockCarryOverDecisionScope(Long projectId) {
        if (projectId == null) {
            return;
        }
        projectMapper.selectOne(
                new QueryWrapper<Project>()
                        .select("id")
                        .eq("id", projectId)
                        .last("FOR UPDATE")
        );
    }

    private ProjectSelfMediaScheduleCarryOver findPendingCarryOver(Long projectId, String sourceMonth, String targetMonth) {
        List<ProjectSelfMediaScheduleCarryOver> rows = carryOverMapper.selectList(
                new QueryWrapper<ProjectSelfMediaScheduleCarryOver>()
                        .eq("project_id", projectId)
                        .eq("source_month", sourceMonth)
                        .eq("target_month", targetMonth)
                        .eq("status", CARRY_OVER_STATUS_PENDING)
                        .orderByAsc("id")
                        .last("LIMIT 1")
        );
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private void applyCarryOverResult(SelfMediaPublishAutoScheduleResponse response,
                                      CarryOverResult result) {
        if (result == null || result.count() <= 0) {
            return;
        }
        response.setDecisionStrategy(STRATEGY_CARRY_OVER);
        response.setCarryOverCreated(result.id() != null);
        response.setCarryOverCount(result.count());
        response.setCarryOverTargetMonth(result.targetMonth());
    }

    private void applyCapacityFields(SelfMediaPublishAutoScheduleResponse response,
                                     SelfMediaPublishAutoScheduleResponse capacity) {
        if (response == null || capacity == null) {
            return;
        }
        response.setAvailableSlotCount(capacity.getAvailableSlotCount());
        response.setDeficitCount(capacity.getDeficitCount());
        response.setEnough(capacity.getEnough());
        response.setRecommendedStrategy(capacity.getRecommendedStrategy());
        response.setSlotGroups(capacity.getSlotGroups());
        response.setRequestedCount(capacity.getRequestedCount());
        response.setNormalRequiredCount(capacity.getNormalRequiredCount());
        response.setPendingCarryOverCount(capacity.getPendingCarryOverCount());
        response.setUnavailableCarryOverCount(capacity.getUnavailableCarryOverCount());
        response.setCarryOverSources(capacity.getCarryOverSources());
        response.setWarnings(capacity.getWarnings());
    }

    private void applyPlanBuildWarnings(SelfMediaPublishAutoScheduleResponse response,
                                        AccountPublishPlanResult planResult) {
        if (response == null || planResult == null) {
            return;
        }
        response.setNormalRequiredCount(planResult.normalRequiredCount());
        response.setPendingCarryOverCount(planResult.pendingCarryOverCount());
        response.setCarryOverSources(planResult.carryOverSources());
        response.setUnavailableCarryOverCount(planResult.unavailableCarryOverCount());
        if (planResult.warnings() != null && !planResult.warnings().isEmpty()) {
            response.getWarnings().addAll(planResult.warnings());
        }
    }

    private void applyCapacitySummary(SelfMediaPublishAutoScheduleResponse response) {
        int requiredCount = safeCount(response.getRequestedCount());
        int availableCount = 0;
        int deficitCount = 0;
        boolean enough = true;
        for (SelfMediaPublishAutoScheduleResponse.SlotGroup group : response.getSlotGroups()) {
            int requested = safeCount(group.getRequestedCount());
            int available = safeCount(group.getAvailableSlotCount());
            availableCount += Math.min(requested, available);
            deficitCount += Math.max(0, requested - available);
            enough = enough && Boolean.TRUE.equals(group.getEnough());
        }
        response.setAvailableSlotCount(availableCount);
        response.setDeficitCount(Math.max(deficitCount, Math.max(0, requiredCount - availableCount)));
        response.setEnough(enough && response.getDeficitCount() == 0);
        response.setRecommendedStrategy(Boolean.TRUE.equals(response.getEnough())
                ? STRATEGY_STRICT_CURRENT_MONTH
                : STRATEGY_CARRY_OVER);
    }

    private void throwCapacityInsufficient(SelfMediaPublishAutoScheduleResponse summary) {
        int requiredCount = safeCount(summary.getRequestedCount());
        int availableCount = safeCount(summary.getAvailableSlotCount());
        int deficitCount = safeCount(summary.getDeficitCount());
        String message = "目标月份剩余可自动排期容量不足：应排 " + requiredCount
                + "，可排 " + availableCount
                + "，缺口 " + deficitCount
                + "。请选择其他月份或由交付负责人走结转补排。";
        throw new BizException(
                ERROR_CODE,
                message,
                400,
                Map.of(
                        "errorCode", ERROR_CAPACITY_INSUFFICIENT,
                        "requiredCount", requiredCount,
                        "availableSlotCount", availableCount,
                        "deficitCount", deficitCount,
                        "recommendedStrategy", firstText(summary.getRecommendedStrategy(), STRATEGY_CARRY_OVER)
                )
        );
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private List<SelfMediaPublishAutoScheduleItemVO> buildPreviewItems(
            List<SelfMediaPublishAutoScheduleResponse.SlotGroup> groups,
            List<AccountPublishPlan> plans
    ) {
        Map<String, List<SelfMediaPublishAutoScheduleResponse.SlotPreview>> previewsByPlatform = new LinkedHashMap<>();
        for (SelfMediaPublishAutoScheduleResponse.SlotGroup group : groups) {
            previewsByPlatform.put(firstText(normalizePlatform(group.getPlatform()), "unknown"), group.getSelectedSlots());
        }
        Map<String, Integer> cursorByPlatform = new LinkedHashMap<>();
        List<SelfMediaPublishAutoScheduleItemVO> items = new ArrayList<>();
        for (AccountPublishPlan plan : plans) {
            String platform = firstText(normalizePlatform(plan.platform()), "unknown");
            int cursor = cursorByPlatform.getOrDefault(platform, 0);
            cursorByPlatform.put(platform, cursor + 1);
            SelfMediaPublishAutoScheduleItemVO item = new SelfMediaPublishAutoScheduleItemVO();
            item.setSelfMediaAccountId(plan.accountId());
            item.setPlatform(platform);
            List<SelfMediaPublishAutoScheduleResponse.SlotPreview> previews = previewsByPlatform.get(platform);
            if (previews != null && cursor < previews.size()) {
                SelfMediaPublishAutoScheduleResponse.SlotPreview preview = previews.get(cursor);
                item.setCalendarDate(preview.getExecutionAt().toLocalDate());
                item.setPlannedPublishAt(preview.getPlannedPublishAt());
                item.setWindowName(preview.getWindowName());
                item.setStatus("planned");
            } else {
                item.setStatus("rejected");
                item.setRejectionCode("PROJECT_AUTO_SCHEDULE_SLOT_UNAVAILABLE");
                item.setRejectionMessage(platformLabel(platform) + "目标月份剩余可自动排期时间不足");
            }
            items.add(item);
        }
        return items;
    }

    private List<BusinessCalendarService.PublishSlot> selectEvenlyFromSlots(List<BusinessCalendarService.PublishSlot> slots,
                                                                            int count) {
        if (count <= 0) {
            return List.of();
        }
        List<BusinessCalendarService.PublishSlot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int slotIndex = Math.min(slots.size() - 1, (int) Math.floor((double) i * slots.size() / count));
            result.add(slots.get(slotIndex));
        }
        return result;
    }

    private boolean isFutureExecutableSlot(BusinessCalendarService.PublishSlot slot,
                                           String platform,
                                           String scheduleStrategy,
                                           LocalDateTime now) {
        if (slot == null || slot.plannedAt() == null || !slot.plannedAt().isAfter(now)) {
            return false;
        }
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalizeText(scheduleStrategy))) {
            return true;
        }
        LocalDateTime plannedPublishAt = resolvePlannedPublishAt(slot, platform, scheduleStrategy);
        int minRemainingMinutes = Math.max(0, scheduleAdapterRouter.rules(platform, scheduleStrategy).minRemainingMinutes());
        return plannedPublishAt != null && plannedPublishAt.isAfter(now.plusMinutes(minRemainingMinutes));
    }

    private boolean isRetryableScheduleStatus(String status) {
        String normalized = normalizeText(status);
        return SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(normalized)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED.equals(normalized)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED.equals(normalized);
    }

    private boolean isPublishResultCheckQueue(String queueKind) {
        return SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK.equals(normalizeText(queueKind));
    }

    private void rejectPublishResultCheckBatchAction(List<ProjectSelfMediaScheduleBatchDetailVO.Item> items,
                                                     String errorCode,
                                                     String message) {
        if (items != null && items.stream().anyMatch(item -> isPublishResultCheckQueue(item.getQueueKind()))) {
            throw new BizException(ERROR_CODE, errorCode + ": " + message);
        }
    }

    private void rejectPublishResultCheckBatchAction(SelfMediaPublishSchedule row,
                                                     String errorCode,
                                                     String message) {
        if (row != null && isPublishResultCheckQueue(row.getQueueKind())) {
            throw new BizException(ERROR_CODE, errorCode + ": " + message);
        }
    }

    private boolean isManualRequiredMarkableScheduleStatus(String status) {
        String normalized = normalizeText(status);
        return SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED.equals(normalized)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED.equals(normalized);
    }

    private boolean isAutoCompensationCandidate(SelfMediaPublishSchedule row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        String status = normalizeText(row.getStatus());
        if (!SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED.equals(status)
                && !SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(status)) {
            return false;
        }
        if (!SelfMediaPublishFailureCodes.isScheduleExecutionRetryable(row.getFailureCode())) {
            return false;
        }
        int attempts = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        return attempts < AUTO_COMPENSATION_MAX_ATTEMPTS;
    }

    private List<ProjectSelfMediaScheduleBatchDetailVO.FailureSummary> buildFailureSummaries(
            List<ProjectSelfMediaScheduleBatchDetailVO.Item> items
    ) {
        Map<String, ProjectSelfMediaScheduleBatchDetailVO.FailureSummary> summaries = new LinkedHashMap<>();
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : items) {
            FailureReason reason = failureReason(item);
            if (reason == null) {
                continue;
            }
            ProjectSelfMediaScheduleBatchDetailVO.FailureSummary summary = summaries.computeIfAbsent(
                    reason.key(),
                    ignored -> {
                        ProjectSelfMediaScheduleBatchDetailVO.FailureSummary created =
                                new ProjectSelfMediaScheduleBatchDetailVO.FailureSummary();
                        created.setCode(reason.code());
                        created.setLabel(reason.label());
                        created.setCategory(reason.category());
                        created.setCount(0);
                        created.setRetryable(reason.retryable());
                        created.setActionHint(reason.actionHint());
                        created.setFirstMessage(reason.message());
                        created.setGroupCode(reason.groupCode());
                        created.setGroupLabel(reason.groupLabel());
                        created.setOperatorAction(reason.operatorAction());
                        return created;
                    }
            );
            summary.setCount((summary.getCount() == null ? 0 : summary.getCount()) + 1);
            if (!StringUtils.hasText(summary.getFirstMessage())) {
                summary.setFirstMessage(reason.message());
            }
        }
        return summaries.values().stream()
                .sorted(Comparator
                        .comparing(ProjectSelfMediaScheduleBatchDetailVO.FailureSummary::getCount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProjectSelfMediaScheduleBatchDetailVO.FailureSummary::getLabel,
                                Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private FailureReason failureReason(ProjectSelfMediaScheduleBatchDetailVO.Item item) {
        if (item == null) {
            return null;
        }
        if ("failed".equals(normalizeText(item.getGenerationStatus()))) {
            String message = item.getGenerationErrorMessage();
            return new FailureReason(
                    "GENERATION_FAILED",
                    "文章生成失败",
                    "generation",
                    false,
                    "检查文章模板、问题池和模型返回后重试失败项。",
                    firstText(message, "文章生成失败"),
                    "ARTICLE_GENERATION",
                    "文章生成问题",
                    "先重新处理失败项；如果连续失败，请检查选题和模板。"
            );
        }
        String code = trimToNull(item.getScheduleFailureCode());
        String message = firstText(item.getScheduleFailureMessage(), code);
        if (!StringUtils.hasText(code) && !StringUtils.hasText(message)) {
            return null;
        }
        String normalizedStatus = normalizeText(item.getScheduleStatus());
        String category = "rejected".equals(normalizedStatus) ? "schedule_rejected" : "schedule_abnormal";
        if (!StringUtils.hasText(code) && StringUtils.hasText(message)) {
            code = SelfMediaPublishFailureCodes.classifyByMessage(message);
        }
        String safeCode = firstText(code, "UNKNOWN_FAILURE");
        Boolean retryable = SelfMediaPublishFailureCodes.retryable(safeCode);
        FailureGroup group = failureGroup(safeCode, message, normalizedStatus);
        return new FailureReason(
                safeCode,
                firstText(SelfMediaPublishFailureCodes.label(safeCode), safeCode),
                category,
                Boolean.TRUE.equals(retryable),
                SelfMediaPublishFailureCodes.actionHint(safeCode),
                message,
                group.code(),
                group.label(),
                group.operatorAction()
        );
    }

    private record FailureReason(String code,
                                 String label,
                                 String category,
                                 Boolean retryable,
                                 String actionHint,
                                 String message,
                                 String groupCode,
                                 String groupLabel,
                                 String operatorAction) {
        String key() {
            return category + "|" + groupCode + "|" + code;
        }
    }

    private FailureGroup failureGroup(String code, String message, String status) {
        String text = (firstText(code, "") + " " + firstText(message, "") + " " + firstText(status, "")).toLowerCase(Locale.ROOT);
        if (text.contains("replaced_by_operator") || text.contains("安全替换") || text.contains("被新的快速分发任务替换")
                || text.contains("被快速分发替换") || text.contains("由平台快速排期替换")
                || text.contains("由手动触发占用")) {
            return new FailureGroup("REPLACED_BY_NEW_TASK", "手动触发已占用", "无需处理这条自动排期。");
        }
        if (text.contains("too_close") || text.contains("remaining") || text.contains("lead")
                || text.contains("过近") || text.contains("提前") || text.contains("时间不足")) {
            return new FailureGroup("PUBLISH_TIME", "发布时间不合适", "改期到下月，或减少本月数量后重新处理。");
        }
        if (text.contains("local_agent") || text.contains("adspower") || text.contains("helper")
                || text.contains("助手") || text.contains("浏览器")) {
            return new FailureGroup("LOCAL_HELPER", "本地助手问题", "确认本地助手已打开、账号匹配后重新处理。");
        }
        if (text.contains("account") || text.contains("auth") || text.contains("credential")
                || text.contains("账号") || text.contains("授权") || text.contains("cookie")) {
            return new FailureGroup("ACCOUNT_CONFIG", "账号配置问题", "检查平台账号、授权和绑定运营后重新处理。");
        }
        if (text.contains("platform") || text.contains("submit") || text.contains("schedule")
                || text.contains("平台") || text.contains("预约") || text.contains("定时")) {
            return new FailureGroup("PLATFORM_REJECTED", "平台没有接受", "查看平台规则，调整发布时间或内容后重新处理。");
        }
        if (text.contains("publish_failed") || text.contains("publish_unknown") || text.contains("发布")) {
            return new FailureGroup("PUBLISH_RESULT", "发布结果异常", "检查平台作品列表，确认是否已发布；必要时重新校验。");
        }
        if ("rejected".equals(status)) {
            return new FailureGroup("SCHEDULE_CREATE", "发布时间安排失败", "调整账号或发布时间后重新处理。");
        }
        return new FailureGroup("OTHER", "其他异常", "查看单条异常信息后决定重新处理、改期或转人工。");
    }

    private record FailureGroup(String code, String label, String operatorAction) {
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

    private AccountPublishPlanResult buildAccountPublishPlans(Project project, String targetMonth, List<Long> accountIds) {
        YearMonth month = parseTargetMonth(targetMonth);
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
        CarryOverLoadResult carryOverLoad = loadPendingCarryOverPlans(project, targetMonth, accountsByPlatform);
        List<AccountPublishPlan> plans = new ArrayList<>(carryOverLoad.plans());
        accountsByPlatform.forEach((platform, accounts) -> {
            CompanyChannelQuotaService.DistributionQuotaView quota =
                    companyChannelQuotaService.selfMediaDistributionQuota(project.getCompanyId(), platform);
            String publishPlatform = firstText(ArticlePromptChannels.normalizeSelfMediaPublishPlatform(platform), platform);
            long activeSchedules = selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                    project.getBrandId(),
                    publishPlatform,
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
        int pendingCarryOverCount = (int) plans.stream()
                .filter(plan -> plan.carryOverId() != null)
                .count();
        int normalRequiredCount = Math.max(0, plans.size() - pendingCarryOverCount);
        return new AccountPublishPlanResult(
                plans,
                normalRequiredCount,
                pendingCarryOverCount,
                carryOverLoad.unavailableCount(),
                carryOverLoad.warnings(),
                carryOverLoad.sources()
        );
    }

    private CarryOverLoadResult loadPendingCarryOverPlans(Project project,
                                                          String targetMonth,
                                                          Map<String, List<SelfMediaAccount>> accountsByPlatform) {
        List<ProjectSelfMediaScheduleCarryOver> rows = carryOverMapper.selectList(
                new QueryWrapper<ProjectSelfMediaScheduleCarryOver>()
                        .eq("project_id", project.getId())
                        .eq("target_month", targetMonth)
                        .eq("status", CARRY_OVER_STATUS_PENDING)
                        .orderByAsc("id")
        );
        if (rows == null || rows.isEmpty()) {
            return new CarryOverLoadResult(List.of(), 0, List.of(), List.of());
        }
        Map<Long, SelfMediaAccount> accountById = new LinkedHashMap<>();
        accountsByPlatform.values().forEach(accounts ->
                accounts.forEach(account -> accountById.put(account.getId(), account)));
        List<AccountPublishPlan> plans = new ArrayList<>();
        int unavailableCount = 0;
        List<String> warnings = new ArrayList<>();
        List<SelfMediaPublishAutoScheduleResponse.CarryOverSource> sources = new ArrayList<>();
        for (ProjectSelfMediaScheduleCarryOver row : rows) {
            sources.add(toCarryOverSource(row));
            List<CarryOverPlan> savedPlans = readCarryOverPlans(row.getCarryOverPlanJson());
            if (savedPlans.isEmpty()) {
                savedPlans = fallbackCarryOverPlans(row, accountsByPlatform);
            }
            List<AccountPublishPlan> rowPlans = new ArrayList<>();
            int rowUnavailableCount = 0;
            for (CarryOverPlan savedPlan : savedPlans) {
                String platform = firstText(normalizePlatform(savedPlan.platform()), null);
                if (!StringUtils.hasText(platform)) {
                    rowUnavailableCount++;
                    continue;
                }
                Long accountId = resolveCarryOverAccountId(savedPlan.accountId(), platform, accountsByPlatform, accountById);
                if (accountId != null) {
                    rowPlans.add(new AccountPublishPlan(accountId, platform, row.getId()));
                } else {
                    rowUnavailableCount++;
                }
            }
            if (rowUnavailableCount > 0) {
                unavailableCount += rowUnavailableCount;
                warnings.add("存在历史结转因平台账号不可用暂无法消费，已继续保持 pending，请补充可用账号后重试。");
                continue;
            }
            plans.addAll(rowPlans);
        }
        return new CarryOverLoadResult(plans, unavailableCount, warnings.stream().distinct().toList(), sources);
    }

    private SelfMediaPublishAutoScheduleResponse.CarryOverSource toCarryOverSource(ProjectSelfMediaScheduleCarryOver row) {
        SelfMediaPublishAutoScheduleResponse.CarryOverSource source =
                new SelfMediaPublishAutoScheduleResponse.CarryOverSource();
        source.setId(row.getId());
        source.setSourceMonth(row.getSourceMonth());
        source.setTargetMonth(row.getTargetMonth());
        source.setCarryOverCount(safeCount(row.getCarryOverCount()));
        source.setConsumedCount(safeCount(row.getConsumedCount()));
        source.setPendingCount(Math.max(0, safeCount(row.getCarryOverCount()) - safeCount(row.getConsumedCount())));
        source.setStatus(row.getStatus());
        return source;
    }

    private List<CarryOverPlan> readCarryOverPlans(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<CarryOverPlan> plans = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                JsonNode accountNode = node.hasNonNull("accountId")
                        ? node.get("accountId")
                        : node.get("selfMediaAccountId");
                Long accountId = accountNode != null && accountNode.canConvertToLong()
                        ? accountNode.asLong()
                        : null;
                String platform = node.hasNonNull("platform") ? node.get("platform").asText(null) : null;
                plans.add(new CarryOverPlan(accountId, platform));
            }
            return plans;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<CarryOverPlan> fallbackCarryOverPlans(ProjectSelfMediaScheduleCarryOver row,
                                                       Map<String, List<SelfMediaAccount>> accountsByPlatform) {
        if (accountsByPlatform.isEmpty()) {
            return List.of();
        }
        String platform = accountsByPlatform.keySet().iterator().next();
        int count = Math.max(0, safeCount(row.getCarryOverCount()) - safeCount(row.getConsumedCount()));
        List<CarryOverPlan> plans = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            plans.add(new CarryOverPlan(null, platform));
        }
        return plans;
    }

    private Long resolveCarryOverAccountId(Long savedAccountId,
                                           String platform,
                                           Map<String, List<SelfMediaAccount>> accountsByPlatform,
                                           Map<Long, SelfMediaAccount> accountById) {
        SelfMediaAccount savedAccount = savedAccountId == null ? null : accountById.get(savedAccountId);
        if (savedAccount != null && platform.equals(normalizePlatform(savedAccount.getPlatform()))) {
            return savedAccount.getId();
        }
        List<SelfMediaAccount> platformAccounts = accountsByPlatform.get(platform);
        if (platformAccounts == null || platformAccounts.isEmpty()) {
            return null;
        }
        return platformAccounts.get(0).getId();
    }

    private void consumePendingCarryOvers(List<AccountPublishPlan> plans) {
        List<Long> carryOverIds = plans.stream()
                .map(AccountPublishPlan::carryOverId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (carryOverIds.isEmpty()) {
            return;
        }
        List<ProjectSelfMediaScheduleCarryOver> rows = carryOverMapper.selectList(
                new QueryWrapper<ProjectSelfMediaScheduleCarryOver>()
                        .in("id", carryOverIds)
                        .eq("status", CARRY_OVER_STATUS_PENDING)
        );
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (ProjectSelfMediaScheduleCarryOver row : rows) {
            ProjectSelfMediaScheduleCarryOver update = new ProjectSelfMediaScheduleCarryOver();
            update.setId(row.getId());
            update.setStatus(CARRY_OVER_STATUS_CONSUMED);
            update.setConsumedCount(safeCount(row.getCarryOverCount()));
            carryOverMapper.updateById(update);
        }
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
            item.setScheduleFailureMessage(operatorFriendlyScheduleFailureMessage(
                    schedule.getFailureCode(),
                    schedule.getFailureMessage()
            ));
            applyClaimDiagnostic(item, schedule);
            applyStatusActionInfo(item, schedule);
        } else {
            SelfMediaPublishScheduleRejectedItemVO rejected = findRejectedItem(rejectedItems, item.getArticleId(), plan);
            if (rejected != null) {
                item.setScheduleStatus("rejected");
                item.setScheduleFailureCode(rejected.getCode());
                item.setScheduleFailureMessage(operatorFriendlyScheduleFailureMessage(
                        rejected.getCode(),
                        firstText(rejected.getMessage(), rejected.getCode())
                ));
            }
            applyStatusActionInfo(item, null);
        }
        applyFailureGroup(item);
        return item;
    }

    private String operatorFriendlyScheduleFailureMessage(String code, String message) {
        String text = firstText(code, "") + " " + firstText(message, "");
        if (text.contains("REPLACED_BY_OPERATOR_QUICK_DISPATCH")
                || text.contains("REPLACED_BY_OPERATOR_QUICK_SCHEDULE")
                || text.contains("运营点击平台快速分发时安全替换")
                || text.contains("运营确认后由平台快速排期替换")
                || text.contains("旧排期已被新的快速分发任务替换")
                || text.contains("旧排期已被快速分发替换")
                || text.contains("当前任务排期已由手动触发占用")) {
            return "当前任务排期已由手动触发占用";
        }
        return message;
    }

    private void applyClaimDiagnostic(ProjectSelfMediaScheduleBatchDetailVO.Item item, SelfMediaPublishSchedule schedule) {
        ClaimDiagnostic diagnostic = claimDiagnostic(schedule);
        item.setClaimDiagnosticCode(diagnostic.code());
        item.setClaimDiagnosticMessage(diagnostic.message());
    }

    private void applyStatusActionInfo(ProjectSelfMediaScheduleBatchDetailVO.Item item, SelfMediaPublishSchedule schedule) {
        List<String> actions = new ArrayList<>();
        String generationStatus = normalizeText(item.getGenerationStatus());
        String scheduleStatus = normalizeText(item.getScheduleStatus());
        if ("failed".equals(generationStatus) && item.getScheduleId() == null) {
            actions.add("重新处理");
            item.setOperatorActionHint("文章生成失败，可先重新处理失败项；如果持续失败，请检查选题、模板或提示词。");
        } else if ("rejected".equals(scheduleStatus)) {
            actions.add("重新处理");
            item.setOperatorActionHint("文章已生成，但发布时间没有安排成功。请调整发布时间或账号后重新处理。");
        } else if (item.getArticleId() != null && item.getScheduleId() == null) {
            actions.add("重新处理");
            item.setOperatorActionHint("文章已生成但还没有安排发布时间，可点击重新处理补上排期。");
        } else if (isRetryableScheduleStatus(scheduleStatus)) {
            if (schedule != null && isPublishResultCheckQueue(schedule.getQueueKind())) {
                actions.add("重新校验");
                item.setOperatorActionHint("发布结果尚未确认，只能重新校验；为避免重复发布，不能改期或忽略。");
            } else {
                actions.add("重新处理");
                actions.add("改期到下月");
                actions.add("忽略");
                if (isManualRequiredMarkableScheduleStatus(scheduleStatus)) {
                    actions.add("转人工");
                }
                item.setOperatorActionHint("这条内容处理异常，可重新处理；如果本月时间不足，建议改期到下月。");
            }
        } else if (SelfMediaPublishScheduleConstants.STATUS_PENDING.equals(scheduleStatus)) {
            item.setOperatorActionHint("已安排好，等待系统到时间后处理。");
        } else if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(scheduleStatus)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE.equals(scheduleStatus)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(scheduleStatus)) {
            item.setOperatorActionHint("已提交到平台或正在确认发布结果，一般无需人工处理。");
        } else if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING.equals(scheduleStatus)) {
            item.setOperatorActionHint("平台已确认发布，系统正在继续补充发布链接。");
        } else if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(scheduleStatus)) {
            item.setOperatorActionHint("已确认发布完成。");
        } else if (SelfMediaPublishScheduleConstants.STATUS_CANCELLED.equals(scheduleStatus)) {
            item.setOperatorActionHint("已取消，系统不会继续处理。");
        } else {
            item.setOperatorActionHint("等待文章生成或后续处理。");
        }
        item.setAllowedActions(actions);
        if (schedule != null && isAutoCompensationCandidate(schedule)) {
            int attempts = schedule.getAttemptCount() == null ? 0 : schedule.getAttemptCount();
            item.setAutoCompensationAvailable(true);
            item.setAutoCompensationRemaining(Math.max(0, AUTO_COMPENSATION_MAX_ATTEMPTS - attempts));
        } else {
            item.setAutoCompensationAvailable(false);
            item.setAutoCompensationRemaining(0);
        }
    }

    private void applyFailureGroup(ProjectSelfMediaScheduleBatchDetailVO.Item item) {
        FailureReason reason = failureReason(item);
        if (reason == null) {
            return;
        }
        item.setFailureGroupCode(reason.groupCode());
        item.setFailureGroupLabel(reason.groupLabel());
        if (!StringUtils.hasText(item.getOperatorActionHint())) {
            item.setOperatorActionHint(reason.actionHint());
        }
    }

    private ProjectSelfMediaScheduleBatchDetailVO.BatchActionPreview buildBatchActionPreview(
            List<ProjectSelfMediaScheduleBatchDetailVO.Item> items,
            String targetMonth
    ) {
        ProjectSelfMediaScheduleBatchDetailVO.BatchActionPreview preview =
                new ProjectSelfMediaScheduleBatchDetailVO.BatchActionPreview();
        int retryFailed = 0;
        int retryAbnormal = 0;
        int manual = 0;
        int reschedule = 0;
        int ignore = 0;
        int unable = 0;
        for (ProjectSelfMediaScheduleBatchDetailVO.Item item : items) {
            boolean generationFailed = "failed".equals(normalizeText(item.getGenerationStatus())) && item.getScheduleId() == null;
            boolean rejected = item.getScheduleId() == null
                    && "rejected".equals(normalizeText(item.getScheduleStatus()))
                    && item.getArticleId() != null;
            boolean generatedWithoutSchedule = item.getScheduleId() == null
                    && item.getArticleId() != null
                    && !"rejected".equals(normalizeText(item.getScheduleStatus()));
            boolean retryableSchedule = item.getScheduleId() != null && isRetryableScheduleStatus(item.getScheduleStatus());
            boolean publishResultCheck = retryableSchedule && isPublishResultCheckQueue(item.getQueueKind());
            if (generationFailed || rejected || generatedWithoutSchedule) {
                retryFailed++;
            }
            if (retryableSchedule) {
                retryAbnormal++;
                if (!publishResultCheck) {
                    reschedule++;
                    ignore++;
                }
            }
            if (item.getScheduleId() != null && isManualRequiredMarkableScheduleStatus(item.getScheduleStatus())) {
                manual++;
            }
            if (!generationFailed && !rejected && !retryableSchedule && item.getScheduleFailureCode() != null) {
                unable++;
            }
        }
        preview.setRetryFailedCount(retryFailed);
        preview.setRetryAbnormalCount(retryAbnormal);
        preview.setManualCount(manual);
        preview.setRescheduleNextMonthCount(reschedule);
        preview.setIgnoreCount(ignore);
        preview.setUnableCount(unable);
        preview.setNextMonth(nextMonthText(targetMonth));
        if (retryFailed > 0) {
            preview.getMessages().add("有 " + retryFailed + " 条可重新生成文章或补上发布时间。");
        }
        if (retryAbnormal > 0) {
            preview.getMessages().add("有 " + retryAbnormal + " 条异常内容可重新处理、改期或忽略。");
        }
        if (manual > 0) {
            preview.getMessages().add("有 " + manual + " 条可转给运营人工处理。");
        }
        if (unable > 0) {
            preview.getMessages().add("有 " + unable + " 条当前不适合批量处理，请查看单条异常。");
        }
        return preview;
    }

    private List<ProjectSelfMediaScheduleBatchDetailVO.StatusRule> scheduleStatusRules() {
        List<ProjectSelfMediaScheduleBatchDetailVO.StatusRule> rules = new ArrayList<>();
        rules.add(statusRule("pending", "等待处理", "已安排好时间，系统会在合适时间处理。", List.of(), "一般无需操作；如果一直不动，请看处理提示。"));
        rules.add(statusRule("filling", "正在准备内容", "本地助手正在打开平台并填写内容。", List.of(), "等待处理完成。"));
        rules.add(statusRule("scheduling", "正在预约发布时间", "系统正在把发布时间提交到平台。", List.of(), "等待平台返回结果。"));
        rules.add(statusRule("scheduled", "已预约", "平台已接受预约发布时间。", List.of(), "等待到发布时间后确认结果。"));
        rules.add(statusRule("schedule_failed", "预约失败", "平台没有接受这次预约。", List.of("重新处理", "转人工", "改期到下月", "忽略"), "优先看异常原因；时间太近时建议改期。"));
        rules.add(statusRule("publish_failed", "发布失败", "到发布时间后未确认发布成功。", List.of("重新处理", "转人工", "改期到下月", "忽略"), "检查平台账号和本地助手后再处理。"));
        rules.add(statusRule("manual_required", "需人工处理", "系统判断继续自动处理意义不大。", List.of("重新处理", "改期到下月", "忽略"), "按异常原因处理后再重新处理。"));
        rules.add(statusRule("cancelled", "已忽略", "运营已忽略或系统已取消。", List.of(), "系统不会继续处理。"));
        rules.add(statusRule("published_url_pending", "已发布待补链接", "平台已确认发布，公开链接尚未回写。", List.of(), "等待系统自动补链；必要时可在排期列表人工补充链接。"));
        rules.add(statusRule("published_confirmed", "已发布", "系统已确认发布成功。", List.of(), "无需处理。"));
        return rules;
    }

    private ProjectSelfMediaScheduleBatchDetailVO.StatusRule statusRule(String status,
                                                                        String label,
                                                                        String meaning,
                                                                        List<String> allowedActions,
                                                                        String hint) {
        ProjectSelfMediaScheduleBatchDetailVO.StatusRule rule = new ProjectSelfMediaScheduleBatchDetailVO.StatusRule();
        rule.setStatus(status);
        rule.setLabel(label);
        rule.setMeaning(meaning);
        rule.setAllowedActions(new ArrayList<>(allowedActions));
        rule.setOperatorHint(hint);
        return rule;
    }

    private String nextMonthText(String targetMonth) {
        try {
            return YearMonth.parse(targetMonth).plusMonths(1).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private ClaimDiagnostic claimDiagnostic(SelfMediaPublishSchedule schedule) {
        if (schedule == null) {
            return new ClaimDiagnostic("NO_SCHEDULE", "暂未生成发布任务");
        }
        String status = normalizeText(schedule.getStatus());
        String queueKind = normalizeText(schedule.getQueueKind());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!requiresLocalAgent(schedule)) {
            return new ClaimDiagnostic("OFFICIAL_API_OR_BACKEND", "该平台可由系统直接处理，不需要打开本地助手");
        }
        if (schedule.getLockedUntil() != null && schedule.getLockedUntil().isAfter(now)) {
            return new ClaimDiagnostic("LOCKED", "正在处理中，预计到 " + schedule.getLockedUntil() + " 后可继续处理");
        }
        if (!SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION.equals(queueKind)
                || !SelfMediaPublishScheduleConstants.STATUS_PENDING.equals(status)) {
            return new ClaimDiagnostic("NOT_CLAIMABLE_STATUS", "当前还不能自动处理，请查看进度或异常原因");
        }
        if (schedule.getNextAttemptAt() != null && schedule.getNextAttemptAt().isAfter(now)) {
            return new ClaimDiagnostic("NOT_DUE", "还没到处理时间，预计 " + schedule.getNextAttemptAt());
        }
        Long brandId = schedule.getBrandId();
        if (brandId == null || brandId <= 0) {
            return new ClaimDiagnostic("BRAND_MISSING", "发布任务缺少品牌归属，请重新生成排期");
        }
        LocalDateTime onlineSince = now.minusMinutes(LOCAL_AGENT_ONLINE_WINDOW_MINUTES);
        long onlineSessions = selfMediaPublishScheduleMapper.countOnlineLocalAgentsServingBrand(
                brandId,
                now,
                onlineSince
        );
        if (onlineSessions <= 0) {
            return new ClaimDiagnostic("LOCAL_AGENT_OFFLINE", "该品牌绑定的本地助手未在线，请在绑定环境所在电脑启动助手");
        }
        long runningLoad = selfMediaPublishScheduleMapper.countLockedByBrandAndStatuses(
                brandId,
                List.of(
                        SelfMediaPublishScheduleConstants.STATUS_FILLING,
                        SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
                        SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT
                ),
                now
        );
        long actualCapacity = selfMediaPublishScheduleMapper.sumOnlineLocalAgentCapacityByBrand(
                brandId,
                now,
                onlineSince
        );
        if (actualCapacity <= 0) {
            return new ClaimDiagnostic("LOCAL_AGENT_CAPACITY_UNAVAILABLE", "该品牌本地助手尚未上报可用容量，请刷新助手状态");
        }
        if (runningLoad >= actualCapacity) {
            return new ClaimDiagnostic("LOCAL_AGENT_CAPACITY_FULL", "本地助手正在处理其他任务，请稍后再试或增加在线助手");
        }
        return new ClaimDiagnostic("CLAIMABLE", "已到处理时间，等待本地助手开始处理");
    }

    private boolean requiresLocalAgent(SelfMediaPublishSchedule schedule) {
        if (schedule == null) {
            return false;
        }
        return scheduleAdapterRouter.contract(schedule.getPlatform())
                .map(contract -> SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(contract.publishChannel()))
                .orElse(true);
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
                firstText(ArticlePromptChannels.normalizeSelfMediaPublishPlatform(plan.platform()), normalizePlatform(plan.platform()))
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
            batch.setStatus(STATUS_FAILED);
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
        return List.of(STATUS_PROCESSING, STATUS_CREATED, STATUS_PARTIAL_FAILED)
                .contains(normalizeText(batch.getStatus()));
    }

    private String normalizePlatform(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(value.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String platformLabel(String platform) {
        return switch (normalizeText(platform)) {
            case "toutiao" -> "今日头条";
            case "baijiahao" -> "百家号";
            case "zhihu" -> "知乎";
            case "xiaohongshu" -> "小红书";
            case "douyin" -> "抖音图文";
            case "wechat", "wechat_mp" -> "微信公众号";
            default -> StringUtils.hasText(platform) ? platform : "自媒体平台";
        };
    }

    void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    private record AccountPublishPlan(Long accountId, String platform, Long carryOverId) {
        private AccountPublishPlan(Long accountId, String platform) {
            this(accountId, platform, null);
        }
    }

    private record AccountPublishDay(Long accountId, LocalDate publishDay) {
    }

    private record AccountPublishPlanResult(List<AccountPublishPlan> plans,
                                            int normalRequiredCount,
                                            int pendingCarryOverCount,
                                            int unavailableCarryOverCount,
                                            List<String> warnings,
                                            List<SelfMediaPublishAutoScheduleResponse.CarryOverSource> carryOverSources) {
    }

    private record CarryOverLoadResult(List<AccountPublishPlan> plans,
                                       int unavailableCount,
                                       List<String> warnings,
                                       List<SelfMediaPublishAutoScheduleResponse.CarryOverSource> sources) {
    }

    private record CarryOverPlan(Long accountId, String platform) {
    }

    private record CarryOverSplit(List<AccountPublishPlan> currentMonthPlans,
                                  List<AccountPublishPlan> carryOverPlans) {
    }

    private record CarryOverResult(Long id,
                                   int count,
                                   String targetMonth,
                                   SelfMediaPublishAutoScheduleResponse capacity,
                                   List<AccountPublishPlan> plans,
                                   String reason) {
    }

    private record GenerationPlan(Long generationBatchId,
                                  Long generationTaskId,
                                  Long selfMediaAccountId,
                                  String platform) {
    }

    private record GenerationPayload(String targetMonth,
                                     String scheduleStrategy,
                                     boolean includeAdjustedWorkdays,
                                     String idempotencyKey,
                                     List<GenerationPlan> plans) {
    }

    private record GeneratedSchedulePlan(GenerationPlan plan, Long articleId) {
    }

    private record ClaimDiagnostic(String code, String message) {
    }
}
