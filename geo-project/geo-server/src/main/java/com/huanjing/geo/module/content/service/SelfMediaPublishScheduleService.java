package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleRejectedItemVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SelfMediaPublishScheduleService {
    private static final int ERROR_CODE = 70040;
    private static final int DEFAULT_INTERVAL_MINUTES = 30;
    private static final int REQUEST_TTL_HOURS = 24;
    private static final int DEFAULT_CLAIM_LIMIT = 10;
    private static final int PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 10;
    private static final int TOUTIAO_PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 130;
    private static final int PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES = 120;
    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final String SETTING_PATH_BROWSER_ENV = "品牌详情 > 自媒体账号 > 指纹浏览器环境";
    private static final Set<String> PLATFORM_SUBMITTED_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM
    );
    private static final Set<String> PUBLISH_RESULT_CONFIRMABLE_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
    );
    private static final Map<String, QueueClaimProfile> CLAIM_PROFILES = Map.of(
            SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION,
            new QueueClaimProfile(
                    List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING
            ),
            SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK,
            new QueueClaimProfile(
                    List.of(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE),
                    SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT
            )
    );

    private final SelfMediaPublishScheduleMapper scheduleMapper;
    private final SelfMediaPublishScheduleRequestMapper requestMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final ProjectMapper projectMapper;
    private final BrowserEnvironmentService browserEnvironmentService;
    private final SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private final SelfMediaPublishScheduleEnvironmentLockService environmentLockService;
    private final ContentDistributionService contentDistributionService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SelfMediaPublishScheduleCreateResponse createSchedules(SelfMediaPublishScheduleCreateRequest request,
                                                                  String idempotencyKeyHeader) {
        ValidatedRequest validated = validateRequest(request);
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(validated.brandId(), operator.getId(), BrandAccessAction.OPERATE);

        String normalizedPayload = normalizedPayload(validated);
        String requestKey = StringUtils.hasText(idempotencyKeyHeader)
                ? idempotencyKeyHeader.trim()
                : sha256(normalizedPayload);
        String normalizedHash = sha256(normalizedPayload);

        SelfMediaPublishScheduleRequest existingRequest = requestMapper.selectByRequestKey(validated.brandId(), requestKey);
        if (existingRequest != null) {
            return responseForExistingRequest(existingRequest);
        }

        SelfMediaPublishScheduleRequest requestRow = createRequestRow(validated, operator.getId(), requestKey,
                normalizedHash, normalizedPayload);
        try {
            requestMapper.insert(requestRow);
        } catch (DuplicateKeyException duplicate) {
            SelfMediaPublishScheduleRequest raced = requestMapper.selectByRequestKey(validated.brandId(), requestKey);
            if (raced != null) {
                return responseForExistingRequest(raced);
            }
            throw duplicate;
        }

        SelfMediaPublishScheduleCreateResponse response = new SelfMediaPublishScheduleCreateResponse();
        response.setRequestId(requestRow.getId());
        response.setRequestIdempotencyKey(requestKey);

        LocalDateTime plannedCursor = validated.windowStart();
        for (Long articleId : validated.articleIds()) {
            ArticleDraft article = articleDraftMapper.selectById(articleId);
            for (Long accountId : validated.accountIds()) {
                Candidate candidate = validateCandidate(validated.brandId(), article, articleId, accountId);
                if (candidate.rejected() != null) {
                    response.getRejectedItems().add(candidate.rejected());
                    continue;
                }
                if (plannedCursor.isAfter(validated.windowEnd())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "SCHEDULE_WINDOW_FULL", "排期时间窗口已满，请扩大时间窗口或减少排期数量", null));
                    continue;
                }
                if (isPlatformScheduleTooClose(validated.strategy(), plannedCursor, candidate.account().getPlatform())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "PLATFORM_SCHEDULE_TIME_TOO_CLOSE", "平台定时发布时间需至少晚于当前时间 2 小时", null));
                    continue;
                }
                SelfMediaPublishSchedule inserted = createScheduleRow(requestRow, operator.getId(), candidate,
                        plannedCursor, validated.strategy());
                if (inserted != null) {
                    response.getCreatedSchedules().add(SelfMediaPublishScheduleVO.from(inserted));
                    plannedCursor = plannedCursor.plusMinutes(validated.intervalMinutes());
                } else {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "ACTIVE_SCHEDULE_EXISTS", "同一文章、账号和计划时间已存在活跃排期", null));
                }
            }
        }

        requestRow.setScheduleCount(response.getCreatedSchedules().size());
        requestRow.setStatus("completed");
        requestMapper.updateById(requestRow);
        return response;
    }

    public Page<SelfMediaPublishScheduleVO> pageSchedules(Long brandId,
                                                          String platform,
                                                          String status,
                                                          Long articleId,
                                                          Long selfMediaAccountId,
                                                          Long current,
                                                          Long size) {
        if (brandId != null && brandId <= 0) {
            fail("INVALID_BRAND", "brandId must be a positive number");
        }
        SysUser operator = currentUserService.requireCurrentUser();

        long pageNo = current == null || current <= 0 ? 1 : current;
        long pageSize = size == null || size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<SelfMediaPublishSchedule> wrapper = new LambdaQueryWrapper<>();
        if (brandId != null) {
            brandAccessService.requireBrandAccess(brandId, operator.getId(), BrandAccessAction.OPERATE);
            wrapper.eq(SelfMediaPublishSchedule::getBrandId, brandId);
        } else {
            List<Long> accessibleBrandIds = brandAccessService.listAccessibleBrandIds(operator.getId(), BrandAccessAction.OPERATE);
            if (accessibleBrandIds == null || accessibleBrandIds.isEmpty()) {
                Page<SelfMediaPublishScheduleVO> empty = new Page<>(pageNo, pageSize, 0);
                empty.setRecords(List.of());
                return empty;
            }
            wrapper.in(SelfMediaPublishSchedule::getBrandId, accessibleBrandIds);
        }
        wrapper.orderByAsc(SelfMediaPublishSchedule::getPlannedPublishAt)
                .orderByDesc(SelfMediaPublishSchedule::getId);
        if (StringUtils.hasText(platform)) {
            wrapper.eq(SelfMediaPublishSchedule::getPlatform, platform.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SelfMediaPublishSchedule::getStatus, status.trim());
        }
        if (articleId != null) {
            wrapper.eq(SelfMediaPublishSchedule::getArticleId, articleId);
        }
        if (selfMediaAccountId != null) {
            wrapper.eq(SelfMediaPublishSchedule::getSelfMediaAccountId, selfMediaAccountId);
        }

        Page<SelfMediaPublishSchedule> data = scheduleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Page<SelfMediaPublishScheduleVO> result = new Page<>(data.getCurrent(), data.getSize(), data.getTotal());
        result.setRecords(data.getRecords().stream().map(SelfMediaPublishScheduleVO::from).toList());
        return result;
    }

    public SelfMediaPublishScheduleVO detail(Long id) {
        return SelfMediaPublishScheduleVO.from(requireScheduleWithAccess(id));
    }

    @Transactional
    public SelfMediaPublishScheduleVO claimNext(String queueKind, int lockMinutes) {
        SelfMediaPublishSchedule claimed = claimNextRow(queueKind, lockMinutes, null, null);
        return claimed == null ? null : SelfMediaPublishScheduleVO.from(claimed);
    }

    @Transactional
    public ClaimedScheduleTask claimNextTaskForLocalAgent(Long operatorId, String platform, int lockMinutes) {
        if (operatorId == null || operatorId <= 0) {
            fail("INVALID_OPERATOR", "operatorId must be a positive number");
        }
        SelfMediaPublishSchedule claimed = claimNextRow(
                SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION,
                lockMinutes,
                operatorId,
                trimToNull(platform)
        );
        if (claimed == null) {
            return null;
        }
        try {
            DistributionTask task = prepareDistributionTaskForSchedule(claimed, operatorId);
            SelfMediaPublishSchedule latest = scheduleMapper.selectById(claimed.getId());
            return new ClaimedScheduleTask(
                    SelfMediaPublishScheduleVO.from(latest == null ? claimed : latest),
                    task
            );
        } catch (RuntimeException ex) {
            markClaimFailed(
                    claimed.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING,
                    "DISTRIBUTION_TASK_PREPARE_FAILED",
                    trimError(ex.getMessage()),
                    null,
                    null
            );
            throw ex;
        }
    }

    @Transactional
    public SelfMediaPublishScheduleVO markDistributionTaskFilled(Long distributionTaskId, String diagnosticsJson) {
        if (distributionTaskId == null || distributionTaskId <= 0) {
            return null;
        }
        SelfMediaPublishSchedule row = scheduleMapper.selectActiveByDistributionTaskId(distributionTaskId);
        if (row == null) {
            return null;
        }
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)) {
            return markClaimedFilledVerified(row.getId(), diagnosticsJson);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markDistributionTaskScheduled(Long distributionTaskId, String diagnosticsJson) {
        if (distributionTaskId == null || distributionTaskId <= 0) {
            return null;
        }
        SelfMediaPublishSchedule row = scheduleMapper.selectActiveByDistributionTaskId(distributionTaskId);
        if (row == null) {
            return null;
        }
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)) {
            markClaimedFilledVerified(row.getId(), diagnosticsJson);
            markClaimedScheduling(row.getId(), diagnosticsJson);
            return markClaimedScheduled(row.getId(), null, diagnosticsJson);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)) {
            markClaimedScheduling(row.getId(), diagnosticsJson);
            return markClaimedScheduled(row.getId(), null, diagnosticsJson);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)) {
            return markClaimedScheduled(row.getId(), null, diagnosticsJson);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        return null;
    }

    private SelfMediaPublishSchedule claimNextRow(String queueKind,
                                                  int lockMinutes,
                                                  Long operatorId,
                                                  String platform) {
        QueueClaimProfile profile = requireClaimProfile(queueKind);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(Math.max(lockMinutes, 1));
        List<SelfMediaPublishSchedule> candidates = operatorId == null
                ? scheduleMapper.selectDueQueueCandidates(queueKind, profile.expectedStatuses(), now, DEFAULT_CLAIM_LIMIT)
                : scheduleMapper.selectDueQueueCandidatesForOperator(
                        queueKind, profile.expectedStatuses(), now, DEFAULT_CLAIM_LIMIT, operatorId, platform);
        for (SelfMediaPublishSchedule candidate : candidates) {
            if (isExpiredPlatformScheduleExecution(candidate, now)) {
                markExpiredPlatformScheduleExecution(candidate, now);
                continue;
            }
            if (!environmentLockService.tryAcquire(candidate.getBrowserEnvironmentId(),
                    candidate.getId(), lockedUntil, now)) {
                continue;
            }
            int updated = scheduleMapper.claimQueueSchedule(
                    candidate.getId(),
                    queueKind,
                    profile.expectedStatuses(),
                    profile.targetStatus(),
                    now,
                    lockedUntil
            );
            if (updated > 0) {
                return scheduleMapper.selectById(candidate.getId());
            }
            environmentLockService.release(candidate.getId());
        }
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markDistributionTaskScheduleFailed(Long distributionTaskId,
                                                                         String failureCode,
                                                                         String failureMessage,
                                                                         String diagnosticsJson) {
        return markDistributionTaskScheduleFailed(distributionTaskId, failureCode, failureMessage, diagnosticsJson, null);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markDistributionTaskScheduleFailed(Long distributionTaskId,
                                                                         String failureCode,
                                                                         String failureMessage,
                                                                         String diagnosticsJson,
                                                                         LocalDateTime nextAttemptAt) {
        if (distributionTaskId == null || distributionTaskId <= 0) {
            return null;
        }
        SelfMediaPublishSchedule row = scheduleMapper.selectActiveByDistributionTaskId(distributionTaskId);
        if (row == null) {
            return null;
        }
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)) {
            row.setLockedUntil(null);
            row.setFailureCode(StringUtils.hasText(failureCode) ? failureCode.trim() : "SCHEDULE_EXECUTION_FAILED");
            row.setFailureMessage(trimToNull(failureMessage));
            row.setDiagnosticsJson(trimToNull(diagnosticsJson));
            if (canRetry(row, nextAttemptAt)) {
                row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
                row.setNextAttemptAt(nextAttemptAt);
            } else {
                row.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
                row.setNextAttemptAt(null);
            }
            scheduleMapper.updateById(row);
            environmentLockService.release(row.getId());
            return SelfMediaPublishScheduleVO.from(row);
        }
        return null;
    }

    private DistributionTask prepareDistributionTaskForSchedule(SelfMediaPublishSchedule schedule, Long operatorId) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(schedule.getSelfMediaAccountId());
        if (account == null) {
            fail("SELF_MEDIA_ACCOUNT_NOT_FOUND", "自媒体账号不存在");
        }
        Map<String, Object> platformOptions = new LinkedHashMap<>();
        platformOptions.put("scheduleId", schedule.getId());
        platformOptions.put("scheduleStrategy", schedule.getScheduleStrategy());
        if (schedule.getPlatformScheduledAt() != null) {
            platformOptions.put("scheduledAt", schedule.getPlatformScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            platformOptions.put("platformScheduledAt", schedule.getPlatformScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        TargetContext.SelfMediaTarget target = new TargetContext.SelfMediaTarget(
                account,
                null,
                List.of(),
                List.of(),
                null,
                null,
                scheduleTaskRequestId(schedule),
                platformOptions
        );
        DistributionTask task = contentDistributionService.distributeToAsOperator(schedule.getArticleId(), target, operatorId);
        if (task == null || task.getId() == null) {
            fail("DISTRIBUTION_TASK_NOT_CREATED", "排期分发任务创建失败");
        }
        if (!task.getId().equals(schedule.getDistributionTaskId())) {
            SelfMediaPublishSchedule row = scheduleMapper.selectById(schedule.getId());
            if (row != null) {
                row.setDistributionTaskId(task.getId());
                row.setUpdatedBy(operatorId);
                row.setUpdatedAt(LocalDateTime.now());
                scheduleMapper.updateById(row);
            }
        }
        return task;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedScheduled(Long id, String platformScheduleId, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_SCHEDULING", "当前排期未处于平台定时设置中");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED);
        row.setPlatformScheduleId(trimToNull(platformScheduleId));
        row.setScheduledAt(LocalDateTime.now());
        row.setLockedUntil(null);
        row.setFailureCode(null);
        row.setFailureMessage(null);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedFilledVerified(Long id, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_FILLING", "当前排期未处于填充中");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED);
        row.setFailureCode(null);
        row.setFailureMessage(null);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedScheduling(Long id, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_FILLED_VERIFIED", "当前排期未完成填充校验");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedPublishCheckUnknown(Long id, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CHECKING_PUBLISH_RESULT", "当前排期未处于发布结果确认中");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setLockedUntil(null);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedPublishedConfirmed(Long id,
                                                                    String platformPublishedUrl,
                                                                    String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CHECKING_PUBLISH_RESULT", "当前排期未处于发布结果确认中");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
        row.setPublishedConfirmedAt(LocalDateTime.now());
        row.setPlatformPublishedUrl(trimToNull(platformPublishedUrl));
        row.setLockedUntil(null);
        row.setFailureCode(null);
        row.setFailureMessage(null);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimedPublishFailed(Long id,
                                                               String failureCode,
                                                               String failureMessage,
                                                               String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CHECKING_PUBLISH_RESULT", "当前排期未处于发布结果确认中");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED);
        row.setLockedUntil(null);
        row.setFailureCode(StringUtils.hasText(failureCode) ? failureCode.trim() : "PUBLISH_RESULT_CHECK_FAILED");
        row.setFailureMessage(trimToNull(failureMessage));
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markClaimFailed(Long id,
                                                      String expectedRunningStatus,
                                                      String failureCode,
                                                      String failureMessage,
                                                      String diagnosticsJson,
                                                      LocalDateTime nextAttemptAt) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!normalize(expectedRunningStatus).equals(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CLAIMED", "当前排期不处于预期执行状态");
        }
        row.setLockedUntil(null);
        row.setFailureCode(StringUtils.hasText(failureCode) ? failureCode.trim() : "SCHEDULE_EXECUTION_FAILED");
        row.setFailureMessage(trimToNull(failureMessage));
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        if (canRetry(row, nextAttemptAt)) {
            row.setStatus(statusBeforeClaim(expectedRunningStatus));
            row.setNextAttemptAt(nextAttemptAt);
        } else {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        }
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO cancel(Long id, String reason) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        String status = normalize(row.getStatus());
        LocalDateTime now = LocalDateTime.now();
        if (SelfMediaPublishScheduleConstants.STATUS_CANCELLED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_ROUTED_TO_SEMI_AUTO.equals(status)) {
            fail("SCHEDULE_STATUS_NOT_CANCELLABLE", "当前排期状态不允许取消");
        }
        row.setFailureCode("CANCELLED_BY_OPERATOR");
        row.setFailureMessage(trimToNull(reason));
        if (PLATFORM_SUBMITTED_STATUSES.contains(status)) {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM);
            row.setCancelRequestedAt(row.getCancelRequestedAt() == null ? now : row.getCancelRequestedAt());
        } else {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_CANCELLED);
            row.setCancelledAt(now);
        }
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO confirmPlatformCancelled(Long id, String reason) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_CANCELLED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        if (!SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM.equals(status)) {
            fail("SCHEDULE_STATUS_NOT_WAITING_PLATFORM_CANCEL", "当前排期不处于待确认平台撤销状态");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_CANCELLED);
        row.setCancelledAt(LocalDateTime.now());
        row.setFailureCode("PLATFORM_CANCEL_CONFIRMED");
        row.setFailureMessage(trimToNull(reason));
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO confirmPublished(Long id, String platformPublishedUrl) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        if (!PUBLISH_RESULT_CONFIRMABLE_STATUSES.contains(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CONFIRMABLE", "当前排期状态不允许确认已发布");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
        row.setPublishedConfirmedAt(LocalDateTime.now());
        row.setPlatformPublishedUrl(trimToNull(platformPublishedUrl));
        row.setFailureCode(null);
        row.setFailureMessage(null);
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO confirmPublishFailed(Long id, String failureCode, String failureMessage) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        if (!PUBLISH_RESULT_CONFIRMABLE_STATUSES.contains(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_CONFIRMABLE", "当前排期状态不允许确认发布失败");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED);
        row.setFailureCode(StringUtils.hasText(failureCode) ? failureCode.trim() : "PUBLISH_RESULT_MANUAL_FAILED");
        row.setFailureMessage(trimToNull(failureMessage));
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    private ValidatedRequest validateRequest(SelfMediaPublishScheduleCreateRequest request) {
        if (request == null || request.getBrandId() == null || request.getBrandId() <= 0) {
            fail("INVALID_BRAND", "brandId must be a positive number");
        }
        List<Long> articleIds = distinctPositive(request.getArticleIds(), "articleIds");
        List<Long> accountIds = distinctPositive(request.getSelfMediaAccountIds(), "selfMediaAccountIds");
        LocalDateTime windowStart = request.getWindowStart();
        LocalDateTime windowEnd = request.getWindowEnd();
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart)) {
            fail("INVALID_SCHEDULE_WINDOW", "排期时间窗口无效");
        }
        String strategy = StringUtils.hasText(request.getScheduleStrategy())
                ? request.getScheduleStrategy().trim().toLowerCase(Locale.ROOT)
                : SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE;
        if (SelfMediaPublishScheduleConstants.STRATEGY_IMMEDIATE_PUBLISH_EXCEPTION.equals(strategy)) {
            fail("IMMEDIATE_PUBLISH_EXCEPTION_DISABLED", "自动点击立即发布属于高风险例外，v1 默认拒绝");
        }
        if (SelfMediaPublishScheduleConstants.STRATEGY_SEMI_AUTO.equals(strategy)) {
            fail("SEMI_AUTO_STRATEGY_NOT_ACCEPTED", "全自动排期创建接口不接受 semi_auto；不支持定时的平台请继续使用半自动分发");
        }
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(strategy)) {
            fail("INVALID_SCHEDULE_STRATEGY", "未知排期策略");
        }
        int interval = request.getMinIntervalMinutes() == null
                ? DEFAULT_INTERVAL_MINUTES
                : request.getMinIntervalMinutes();
        if (interval <= 0) {
            fail("INVALID_INTERVAL", "最小错峰间隔必须大于 0");
        }
        return new ValidatedRequest(request.getBrandId(), articleIds, accountIds, windowStart, windowEnd, strategy, interval);
    }

    private SelfMediaPublishSchedule requireScheduleWithAccess(Long id) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(row.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        return row;
    }

    private SelfMediaPublishSchedule requireSchedule(Long id) {
        if (id == null || id <= 0) {
            fail("INVALID_SCHEDULE_ID", "schedule id must be a positive number");
        }
        SelfMediaPublishSchedule row = scheduleMapper.selectById(id);
        if (row == null) {
            fail("SCHEDULE_NOT_FOUND", "排期不存在");
        }
        return row;
    }

    private QueueClaimProfile requireClaimProfile(String queueKind) {
        if (!StringUtils.hasText(queueKind)) {
            fail("INVALID_QUEUE_KIND", "queueKind must not be blank");
        }
        QueueClaimProfile profile = CLAIM_PROFILES.get(queueKind.trim());
        if (profile == null) {
            fail("INVALID_QUEUE_KIND", "未知排期队列类型");
        }
        return profile;
    }

    private boolean canRetry(SelfMediaPublishSchedule row, LocalDateTime nextAttemptAt) {
        int attempts = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        int maxAttempts = row.getMaxAttempts() == null ? 1 : row.getMaxAttempts();
        return attempts < maxAttempts && nextAttemptAt != null;
    }

    private int resolveMaxAttempts(String platform, String strategy) {
        if (SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))
                && "toutiao".equalsIgnoreCase(String.valueOf(platform))) {
            return 2;
        }
        return 1;
    }

    private String statusBeforeClaim(String expectedRunningStatus) {
        String status = normalize(expectedRunningStatus);
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)) {
            return SelfMediaPublishScheduleConstants.STATUS_PENDING;
        }
        if (SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)) {
            return SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE;
        }
        fail("SCHEDULE_STATUS_NOT_RETRYABLE", "当前排期执行状态不支持重试");
        return status;
    }

    private Candidate validateCandidate(Long brandId, ArticleDraft article, Long articleId, Long accountId) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(accountId);
        String platform = account == null ? null : account.getPlatform();
        if (article == null) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_NOT_FOUND", "文章不存在", null));
        }
        if (!ACTIVE_ARTICLE_STATUS.contains(normalize(article.getStatus()))) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_NOT_READY", "仅已就绪或未发布文章可创建自动排期", null));
        }
        Project project = article.getProjectId() == null ? null : projectMapper.selectById(article.getProjectId());
        if (project == null || !brandId.equals(project.getBrandId())) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_BRAND_MISMATCH", "文章不属于当前品牌", null));
        }
        if (account == null) {
            return Candidate.rejected(rejected(articleId, accountId, null,
                    "SELF_MEDIA_ACCOUNT_NOT_FOUND", "自媒体账号不存在", null));
        }
        if (!brandId.equals(account.getBrandId())) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "SELF_MEDIA_ACCOUNT_BRAND_MISMATCH", "自媒体账号不属于当前品牌", null));
        }
        if (!"active".equalsIgnoreCase(String.valueOf(account.getStatus()))) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "SELF_MEDIA_ACCOUNT_INACTIVE", "自媒体账号未启用", null));
        }
        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                scheduleCapabilityService.readiness(platform);
        if (!readiness.ready()) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    readiness.code(), readiness.message(), "全自动排期 > 平台能力验证"));
        }
        BrowserEnvironmentAccount binding;
        try {
            binding = browserEnvironmentService.validateForTaskCreation(account);
        } catch (BizException ex) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    errorCodeFrom(ex), ex.getMessage(), SETTING_PATH_BROWSER_ENV));
        }
        if (binding == null) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", "该自媒体账号未绑定指纹浏览器环境", SETTING_PATH_BROWSER_ENV));
        }
        return new Candidate(article, account, binding, null);
    }

    private SelfMediaPublishSchedule createScheduleRow(SelfMediaPublishScheduleRequest requestRow,
                                                       Long operatorId,
                                                       Candidate candidate,
                                                       LocalDateTime plannedAt,
                                                       String strategy) {
        String baseKey = baseIdempotencyKey(candidate.article().getId(), candidate.account().getId(), plannedAt, strategy);
        SelfMediaPublishSchedule active = scheduleMapper.selectActiveByBaseIdempotencyKey(
                baseKey, new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES));
        if (active != null) {
            return null;
        }

        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setRequestId(requestRow.getId());
        row.setRequestIdempotencyKey(requestRow.getRequestIdempotencyKey());
        row.setArticleId(candidate.article().getId());
        row.setBrandId(requestRow.getBrandId());
        row.setSelfMediaAccountId(candidate.account().getId());
        row.setBrowserEnvironmentId(candidate.binding().getBrowserEnvironmentId());
        row.setBrowserEnvironmentAccountId(candidate.binding().getId());
        row.setPlatform(candidate.account().getPlatform());
        row.setScheduleStrategy(strategy);
        row.setPlannedPublishAt(plannedAt);
        row.setPlatformScheduledAt(plannedAt);
        row.setScheduleDriftSeconds(0);
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setQueuePriority(100);
        row.setBaseIdempotencyKey(baseKey);
        row.setGenerationNo(nextGenerationNo(baseKey));
        row.setAttemptCount(0);
        row.setMaxAttempts(resolveMaxAttempts(candidate.account().getPlatform(), strategy));
        row.setNextAttemptAt(resolveScheduleExecutionAttemptTime(plannedAt, candidate.account().getPlatform(), strategy));
        row.setCreatedBy(operatorId);
        row.setUpdatedBy(operatorId);

        try {
            scheduleMapper.insert(row);
            return row;
        } catch (DuplicateKeyException duplicate) {
            return null;
        }
    }

    private SelfMediaPublishScheduleCreateResponse responseForExistingRequest(SelfMediaPublishScheduleRequest requestRow) {
        SelfMediaPublishScheduleCreateResponse response = new SelfMediaPublishScheduleCreateResponse();
        response.setRequestId(requestRow.getId());
        response.setRequestIdempotencyKey(requestRow.getRequestIdempotencyKey());
        List<SelfMediaPublishSchedule> rows = scheduleMapper.selectByRequestId(requestRow.getId());
        response.setExistingSchedules(rows.stream().map(SelfMediaPublishScheduleVO::from).toList());
        return response;
    }

    private SelfMediaPublishScheduleRequest createRequestRow(ValidatedRequest request,
                                                            Long operatorId,
                                                            String requestKey,
                                                            String normalizedHash,
                                                            String normalizedPayload) {
        SelfMediaPublishScheduleRequest row = new SelfMediaPublishScheduleRequest();
        row.setBrandId(request.brandId());
        row.setOperatorId(operatorId);
        row.setRequestIdempotencyKey(requestKey);
        row.setNormalizedRequestHash(normalizedHash);
        row.setRequestPayload(normalizedPayload);
        row.setStatus("created");
        row.setScheduleCount(0);
        row.setExpiresAt(LocalDateTime.now().plusHours(REQUEST_TTL_HOURS));
        return row;
    }

    private Integer nextGenerationNo(String baseKey) {
        Integer max = scheduleMapper.selectMaxGenerationNo(baseKey);
        return max == null ? 1 : max + 1;
    }

    private LocalDateTime resolveScheduleExecutionAttemptTime(LocalDateTime plannedAt, String platform, String strategy) {
        int leadMinutes = resolveScheduleExecutionLeadMinutes(platform, strategy);
        LocalDateTime fillAt = plannedAt.minusMinutes(leadMinutes);
        LocalDateTime now = LocalDateTime.now();
        return fillAt.isBefore(now) ? now : fillAt;
    }

    private int resolveScheduleExecutionLeadMinutes(String platform, String strategy) {
        if (SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))
                && "toutiao".equals(normalize(platform))) {
            return TOUTIAO_PLATFORM_SCHEDULE_FILL_LEAD_MINUTES;
        }
        return PLATFORM_SCHEDULE_FILL_LEAD_MINUTES;
    }

    private boolean isExpiredPlatformScheduleExecution(SelfMediaPublishSchedule row, LocalDateTime now) {
        if (row == null || !SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION.equals(row.getQueueKind())) {
            return false;
        }
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(row.getScheduleStrategy()))) {
            return false;
        }
        LocalDateTime platformScheduledAt = row.getPlatformScheduledAt();
        return platformScheduledAt != null
                && !platformScheduledAt.isAfter(now.plusMinutes(PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES));
    }

    private boolean isPlatformScheduleTooClose(String strategy, LocalDateTime plannedAt, String platform) {
        return SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))
                && "toutiao".equals(normalize(platform))
                && plannedAt != null
                && !plannedAt.isAfter(LocalDateTime.now().plusMinutes(PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES));
    }

    private void markExpiredPlatformScheduleExecution(SelfMediaPublishSchedule row, LocalDateTime now) {
        SelfMediaPublishSchedule latest = scheduleMapper.selectById(row.getId());
        if (latest == null || !SelfMediaPublishScheduleConstants.STATUS_PENDING.equals(normalize(latest.getStatus()))) {
            return;
        }
        latest.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        latest.setLockedUntil(null);
        latest.setNextAttemptAt(null);
        latest.setFailureCode("PLATFORM_SCHEDULE_TIME_EXPIRED");
        latest.setFailureMessage("平台定时发布时间已过期或过近，无法再自动设置定时发布");
        latest.setDiagnosticsJson(diagnosticsJson("expiredAt", now, "reason", "platform schedule time expired"));
        scheduleMapper.updateById(latest);
    }

    private String normalizedPayload(ValidatedRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("brandId", request.brandId());
        payload.put("articleIds", request.articleIds());
        payload.put("selfMediaAccountIds", request.accountIds());
        payload.put("windowStart", request.windowStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("windowEnd", request.windowEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("scheduleStrategy", request.strategy());
        payload.put("minIntervalMinutes", request.intervalMinutes());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }

    private String baseIdempotencyKey(Long articleId, Long accountId, LocalDateTime plannedAt, String strategy) {
        return sha256(articleId + "|" + accountId + "|" + plannedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "|" + strategy);
    }

    private String scheduleTaskRequestId(SelfMediaPublishSchedule schedule) {
        return "schedule-" + schedule.getId() + "-gen-" + schedule.getGenerationNo();
    }

    private List<Long> distinctPositive(List<Long> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            fail("INVALID_" + fieldName.toUpperCase(Locale.ROOT), fieldName + " must not be empty");
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                fail("INVALID_" + fieldName.toUpperCase(Locale.ROOT), fieldName + " contains invalid id");
            }
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    private SelfMediaPublishScheduleRejectedItemVO rejected(Long articleId,
                                                           Long accountId,
                                                           String platform,
                                                           String code,
                                                           String message,
                                                           String settingPath) {
        return SelfMediaPublishScheduleRejectedItemVO.builder()
                .articleId(articleId)
                .selfMediaAccountId(accountId)
                .platform(platform)
                .code(code)
                .message(message)
                .settingPath(settingPath)
                .build();
    }

    private String errorCodeFrom(BizException ex) {
        Object data = ex.getData();
        if (data instanceof Map<?, ?> map) {
            Object code = map.get("code");
            if (code != null && StringUtils.hasText(String.valueOf(code))) {
                return String.valueOf(code);
            }
        }
        return "ENVIRONMENT_ACCOUNT_NOT_READY";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimError(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }

    private String diagnosticsJson(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object value = values[i + 1];
            payload.put(String.valueOf(values[i]), value instanceof LocalDateTime time
                    ? time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : value);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void touch(SelfMediaPublishSchedule row) {
        row.setUpdatedBy(currentUserService.requireCurrentUser().getId());
        row.setUpdatedAt(LocalDateTime.now());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void fail(String code, String message) {
        throw new BizException(ERROR_CODE, message, 200, Map.of("code", code));
    }

    private record ValidatedRequest(Long brandId,
                                    List<Long> articleIds,
                                    List<Long> accountIds,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    String strategy,
                                    int intervalMinutes) {
    }

    private record Candidate(ArticleDraft article,
                             SelfMediaAccount account,
                             BrowserEnvironmentAccount binding,
                             SelfMediaPublishScheduleRejectedItemVO rejected) {
        static Candidate rejected(SelfMediaPublishScheduleRejectedItemVO item) {
            return new Candidate(null, null, null, item);
        }
    }

    private record QueueClaimProfile(List<String> expectedStatuses, String targetStatus) {
    }

    public record ClaimedScheduleTask(SelfMediaPublishScheduleVO schedule, DistributionTask task) {
    }
}
