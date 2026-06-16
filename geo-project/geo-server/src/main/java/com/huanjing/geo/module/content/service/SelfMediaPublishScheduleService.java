package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.SelfMediaPublishFailureCodes;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.dto.SelfMediaPlatformQuickScheduleRequest;
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
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaAutomationOverviewVO;
import com.huanjing.geo.module.content.vo.SelfMediaPlatformQuickScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleRejectedItemVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
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
import java.time.YearMonth;
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
    private static final int QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES = 3;
    private static final int QUICK_SCHEDULE_SLOT_LOOKAHEAD_HOURS = 12;
    private static final int QUICK_DISPATCH_MANUAL_START_DELAY_MINUTES = 2;
    private static final int REQUEST_TTL_HOURS = 24;
    private static final int DEFAULT_CLAIM_LIMIT = 10;
    private static final int LOCAL_AGENT_ONLINE_WINDOW_MINUTES = 5;
    private static final int LOCAL_AGENT_ASSUMED_CAPACITY = 1;
    private static final int THIRD_PARTY_SOURCE_OVERVIEW_LIMIT = 20;
    private static final int QUICK_DISPATCH_REPLACE_PROTECTION_MINUTES = 10;
    private static final int PUBLISH_CHECK_TOTAL_ATTEMPTS = 4;
    private static final int[] PUBLISH_CHECK_RETRY_DELAYS_MINUTES = {5, 15};
    private static final int[] SCHEDULE_EXECUTION_RETRY_DELAYS_MINUTES = {3, 8};
    private static final Set<String> ACTIVE_ARTICLE_STATUS = Set.of("approved", "unpublished");
    private static final Set<String> LOCKED_ARTICLE_STATUS = Set.of("published", "distributed");
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
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED
    );
    private static final Set<String> PUBLISH_RESULT_RECHECKABLE_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED
    );
    private static final Set<String> SCHEDULE_EXECUTION_RETRYABLE_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_PENDING,
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED
    );
    private static final Set<String> MANUAL_MARKABLE_STATUSES = Set.of(
            SelfMediaPublishScheduleConstants.STATUS_PENDING,
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
            SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED
    );
    private static final List<String> LOCAL_AGENT_RUNNING_STATUSES = List.of(
            SelfMediaPublishScheduleConstants.STATUS_FILLING,
            SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
            SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
            SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT
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
    private final BrandMapper brandMapper;
    private final BrowserEnvironmentService browserEnvironmentService;
    private final SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private final SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private final SelfMediaPublishScheduleAlertService alertService;
    private final SelfMediaPublishScheduleEnvironmentLockService environmentLockService;
    private final ContentDistributionService contentDistributionService;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final BrandAccessService brandAccessService;
    private final CurrentUserService currentUserService;
    private final LocalAgentSessionMapper localAgentSessionMapper;
    private final BusinessCalendarService businessCalendarService;
    private final ThirdPartySubjectRotationService thirdPartySubjectRotationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SelfMediaPublishScheduleCreateResponse createSchedules(SelfMediaPublishScheduleCreateRequest request,
                                                                  String idempotencyKeyHeader) {
        ValidatedRequest validated = validateRequest(request);
        SysUser operator = currentUserService.requireCurrentUser();
        brandAccessService.requireBrandAccess(validated.brandId(), operator.getId(), BrandAccessAction.OPERATE);
        return createSchedulesInternal(validated, operator.getId(), idempotencyKeyHeader);
    }

    @Transactional
    public SelfMediaPublishScheduleCreateResponse createSystemSchedules(SelfMediaPublishScheduleCreateRequest request,
                                                                        String idempotencyKeyHeader,
                                                                        Long operatorId) {
        ValidatedRequest validated = validateRequest(request);
        if (operatorId == null || operatorId <= 0) {
            fail("INVALID_OPERATOR", "operatorId must be a positive number");
        }
        return createSchedulesInternal(validated, operatorId, idempotencyKeyHeader);
    }

    @Transactional
    public SelfMediaPlatformQuickScheduleResponse previewPlatformQuickSchedule(SelfMediaPlatformQuickScheduleRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        QuickSchedulePlan plan = buildQuickSchedulePlan(request, operator.getId());
        return plan.response();
    }

    @Transactional
    public SelfMediaPlatformQuickScheduleResponse createPlatformQuickSchedule(SelfMediaPlatformQuickScheduleRequest request,
                                                                             String idempotencyKeyHeader) {
        SysUser operator = currentUserService.requireCurrentUser();
        QuickSchedulePlan plan = buildQuickSchedulePlan(request, operator.getId());
        if ("replace_required".equals(plan.response().getAction())) {
            if (!Boolean.TRUE.equals(request.getReplaceNextScheduled())) {
                return plan.response();
            }
            SelfMediaPublishSchedule replaceTarget = scheduleMapper.selectById(plan.response().getReplaceScheduleId());
            if (replaceTarget == null) {
                fail("REPLACE_TARGET_NOT_AVAILABLE", "可替换排期已开始执行或不存在，请刷新后重试");
            }
            LocalDateTime now = LocalDateTime.now();
            PeriodWindow replacementMonth = currentMonth(now);
            int replaced = scheduleMapper.cancelReplaceablePendingSchedule(
                    replaceTarget.getId(),
                    plan.response().getBrandId(),
                    plan.response().getPlatform(),
                    replacementMonth.start(),
                    replacementMonth.end(),
                    now
            );
            if (replaced <= 0) {
                fail("REPLACE_TARGET_NOT_AVAILABLE", "可替换排期已开始执行或不存在，请刷新后重试");
            }
            refundScheduleQuotaIfPresent(replaceTarget);
            plan = buildQuickSchedulePlan(request, operator.getId(), true);
        } else if (!"ready".equals(plan.response().getAction())) {
            return plan.response();
        }
        SelfMediaPublishScheduleCreateResponse created = createQuickSchedule(plan, operator.getId(), idempotencyKeyHeader);
        SelfMediaPlatformQuickScheduleResponse response = plan.response();
        response.setAction(created.getCreatedSchedules().isEmpty() ? "rejected" : "created");
        response.setCode(created.getCreatedSchedules().isEmpty() ? "QUICK_SCHEDULE_NOT_CREATED" : "QUICK_SCHEDULE_CREATED");
        response.setMessage(created.getCreatedSchedules().isEmpty()
                ? firstRejectedMessage(created)
                : "已创建平台快速排期，系统将按品牌安全间隔自动打开 AdsPower 环境处理");
        response.setCreateResponse(created);
        return response;
    }

    @Transactional
    public SelfMediaPlatformQuickScheduleResponse dispatchPlatformQuickSchedule(SelfMediaPlatformQuickScheduleRequest request,
                                                                               String idempotencyKeyHeader) {
        SysUser operator = currentUserService.requireCurrentUser();
        QuickSchedulePlan plan = buildQuickSchedulePlan(request, operator.getId());
        if (!List.of("ready", "replace_required").contains(plan.response().getAction())) {
            return plan.response();
        }

        SelfMediaPublishSchedule replaced = null;
        LocalDateTime now = LocalDateTime.now();
        PeriodWindow replacementMonth = currentMonth(now);
        if ("replace_required".equals(plan.response().getAction())) {
            SelfMediaPublishSchedule replaceable = scheduleMapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                    plan.response().getBrandId(),
                    plan.response().getPlatform(),
                    replacementMonth.start(),
                    replacementMonth.end(),
                    now,
                    now.plusMinutes(QUICK_DISPATCH_REPLACE_PROTECTION_MINUTES)
            );
            replaced = cancelSafeReplaceableSchedule(
                    replaceable == null ? null : replaceable.getId(),
                    plan.response().getBrandId(),
                    plan.response().getPlatform(),
                    replacementMonth,
                    now
            );
            plan = buildQuickSchedulePlan(request, operator.getId(), true);
            if (!"ready".equals(plan.response().getAction())) {
                return plan.response();
            }
        } else {
            SelfMediaPublishSchedule replaceable = scheduleMapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                    plan.response().getBrandId(),
                    plan.response().getPlatform(),
                    replacementMonth.start(),
                    replacementMonth.end(),
                    now,
                    now.plusMinutes(QUICK_DISPATCH_REPLACE_PROTECTION_MINUTES)
            );
            if (replaceable != null) {
                replaced = cancelSafeReplaceableSchedule(
                        replaceable.getId(),
                        plan.response().getBrandId(),
                        plan.response().getPlatform(),
                        replacementMonth,
                        now
                );
                plan = buildQuickSchedulePlan(request, operator.getId(), true);
                if (!"ready".equals(plan.response().getAction())) {
                    return plan.response();
                }
            }
        }

        plan = withQuickDispatchTiming(plan, now);
        SelfMediaPublishScheduleCreateResponse created = createQuickSchedule(plan, operator.getId(), idempotencyKeyHeader);
        SelfMediaPlatformQuickScheduleResponse response = plan.response();
        response.setAction(created.getCreatedSchedules().isEmpty() ? "rejected" : "created");
        response.setCode(created.getCreatedSchedules().isEmpty() ? "QUICK_DISPATCH_NOT_CREATED" : "QUICK_DISPATCH_CREATED");
        response.setMessage(created.getCreatedSchedules().isEmpty()
                ? firstRejectedMessage(created)
                : quickDispatchCreatedMessage(response, replaced));
        response.setReplaceScheduleId(replaced == null ? null : replaced.getId());
        response.setCreateResponse(created);
        return response;
    }

    private SelfMediaPublishScheduleCreateResponse createSchedulesInternal(ValidatedRequest validated,
                                                                          Long operatorId,
                                                                          String idempotencyKeyHeader) {
        String normalizedPayload = normalizedPayload(validated);
        String requestKey = StringUtils.hasText(idempotencyKeyHeader)
                ? idempotencyKeyHeader.trim()
                : sha256(normalizedPayload);
        String normalizedHash = sha256(normalizedPayload);

        SelfMediaPublishScheduleRequest existingRequest = requestMapper.selectByRequestKey(validated.brandId(), requestKey);
        if (existingRequest != null) {
            return responseForExistingRequest(existingRequest);
        }

        SelfMediaPublishScheduleRequest requestRow = createRequestRow(validated, operatorId, requestKey,
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
        LocalDateTime executionCursor = validated.executionWindowStart();
        QuotaPrecheck quotaPrecheck = quotaPrecheck(validated.brandId());
        List<ScheduleQuotaReservation> quotaReservations = new ArrayList<>();
        for (Long articleId : validated.articleIds()) {
            ArticleDraft article = articleDraftMapper.selectById(articleId);
            for (Long accountId : validated.accountIds()) {
                Candidate candidate = validateCandidate(validated.brandId(), article, articleId, accountId, validated.strategy());
                if (candidate.rejected() != null) {
                    response.getRejectedItems().add(candidate.rejected());
                    continue;
                }
                if (plannedCursor.isAfter(validated.windowEnd())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "SCHEDULE_WINDOW_FULL", "排期时间窗口已满，请扩大时间窗口或减少排期数量", null));
                    continue;
                }
                if (executionCursor.isAfter(validated.executionWindowEnd())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "SCHEDULE_EXECUTION_WINDOW_FULL", "执行填充时间窗口已满，请扩大时间窗口或减少排期数量", null));
                    continue;
                }
                int requiredLeadMinutes = requiredPlatformScheduleCreateLeadMinutes(
                        validated.strategy(),
                        candidate.account().getPlatform()
                );
                LocalDateTime validationBaseTime = validated.hasExplicitExecutionWindow()
                        ? executionCursor
                        : LocalDateTime.now();
                if (isPlatformScheduleTooClose(validated.strategy(), validationBaseTime, plannedCursor, candidate.account().getPlatform())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "PLATFORM_SCHEDULE_TIME_TOO_CLOSE",
                            "平台定时发布时间需至少晚于执行填充时间 " + minutesText(requiredLeadMinutes),
                            null));
                    continue;
                }
                int maxRemainingMinutes = maxPlatformScheduleRemainingMinutes(
                        validated.strategy(),
                        candidate.account().getPlatform()
                );
                if (isPlatformScheduleTooFar(validated.strategy(), validationBaseTime, plannedCursor, candidate.account().getPlatform())) {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "PLATFORM_SCHEDULE_TIME_TOO_FAR",
                            "平台定时发布时间最多支持执行填充时间后 " + minutesText(maxRemainingMinutes),
                            null));
                    continue;
                }
                SelfMediaPublishScheduleRejectedItemVO quotaRejected = quotaPrecheck.check(articleId, accountId,
                        candidate.account().getPlatform());
                if (quotaRejected != null) {
                    response.getRejectedItems().add(quotaRejected);
                    continue;
                }
                SelfMediaPublishSchedule inserted = createScheduleRow(requestRow, operatorId, candidate,
                        plannedCursor, executionCursor, validated.strategy());
                if (inserted != null) {
                    quotaPrecheck.consume(candidate.account().getPlatform());
                    quotaReservations.add(new ScheduleQuotaReservation(
                            inserted.getId(),
                            candidate.article().getProjectId(),
                            inserted.getPlatform()
                    ));
                    response.getCreatedSchedules().add(SelfMediaPublishScheduleVO.from(inserted));
                    plannedCursor = plannedCursor.plusMinutes(validated.intervalMinutes());
                    executionCursor = executionCursor.plusMinutes(validated.intervalMinutes());
                } else {
                    response.getRejectedItems().add(rejected(articleId, accountId, candidate.account().getPlatform(),
                            "ACTIVE_SCHEDULE_EXISTS", "同一文章、账号和计划时间已存在活跃排期", null));
                }
            }
        }

        reserveScheduleQuotas(quotaPrecheck.companyId, quotaReservations);
        requestRow.setScheduleCount(response.getCreatedSchedules().size());
        requestRow.setStatus("completed");
        requestMapper.updateById(requestRow);
        return response;
    }

    private QuickSchedulePlan buildQuickSchedulePlan(SelfMediaPlatformQuickScheduleRequest request, Long operatorId) {
        return buildQuickSchedulePlan(request, operatorId, false);
    }

    private QuickSchedulePlan buildQuickSchedulePlan(SelfMediaPlatformQuickScheduleRequest request,
                                                     Long operatorId,
                                                     boolean quotaReplacementAlreadyApplied) {
        if (request == null || request.getArticleId() == null || request.getArticleId() <= 0) {
            fail("INVALID_ARTICLE", "articleId must be a positive number");
        }
        String platform = normalize(request.getPlatform());
        if (!StringUtils.hasText(platform)) {
            fail("INVALID_PLATFORM", "platform must not be blank");
        }
        ArticleDraft article = articleDraftMapper.selectById(request.getArticleId());
        if (article == null) {
            return quickResponse("rejected", "ARTICLE_NOT_FOUND", "文章不存在", request.getArticleId(), null,
                    platform, null, null, null, null);
        }
        Project project = article.getProjectId() == null ? null : projectMapper.selectById(article.getProjectId());
        if (project == null || project.getBrandId() == null) {
            return quickResponse("rejected", "ARTICLE_BRAND_MISSING", "当前文章未绑定品牌，无法创建平台排期",
                    article.getId(), null, platform, null, null, null, null);
        }
        Long brandId = project.getBrandId();
        brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE);
        if (!platformsByChannel(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION).contains(platform)) {
            return quickResponse("rejected", "PLATFORM_NOT_AUTOMATED",
                    platformLabel(platform) + "当前不支持 AdsPower 自动排期，请使用该平台已有分发方式",
                    article.getId(), brandId, platform, null, null, null, null);
        }
        String incompatibleMessage = articlePlatformIncompatibleMessage(article, platform);
        if (StringUtils.hasText(incompatibleMessage)) {
            return quickResponse("article_type_mismatch", "ARTICLE_PLATFORM_MISMATCH", incompatibleMessage,
                    article.getId(), brandId, platform, null, null, null, null);
        }
        SelfMediaAccount account = selectActivePlatformAccount(brandId, platform);
        if (account == null) {
            return quickResponse("account_or_environment_not_ready", "SELF_MEDIA_ACCOUNT_NOT_FOUND",
                    "当前品牌暂无可用的" + platformLabel(platform) + "账号，无法创建排期",
                    article.getId(), brandId, platform, null, null, null, null);
        }
        String strategy = quickScheduleStrategy(platform);
        Candidate candidate = validateCandidate(brandId, article, article.getId(), account.getId(), strategy);
        if (candidate.rejected() != null) {
            return quickResponse(actionForRejected(candidate.rejected().getCode()), candidate.rejected().getCode(),
                    candidate.rejected().getMessage(), article.getId(), brandId, platform, account.getId(),
                    null, null, null);
        }
        LocalDateTime now = LocalDateTime.now();
        PeriodWindow month = currentMonth(now);
        if (!quotaReplacementAlreadyApplied) {
            SelfMediaPublishScheduleRejectedItemVO quotaRejected = quotaPrecheck(brandId)
                    .check(article.getId(), account.getId(), platform);
            if (quotaRejected != null) {
                SelfMediaPublishSchedule replaceable = scheduleMapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(
                        brandId, platform, month.start(), month.end(), now);
                if (replaceable != null) {
                    return quickResponse("replace_required", "MONTH_PLATFORM_SCHEDULE_ALREADY_PLANNED",
                            "该平台本月文章已做排期处理，若继续发布将替换已排期文章，是否继续？",
                            article.getId(), brandId, platform, account.getId(), replaceable.getId(),
                            replaceable.getPlannedPublishAt(), replaceable.getNextAttemptAt());
                }
                return quickResponse("quota_exhausted", quotaRejected.getCode(),
                        platformLabel(platform) + "本月可发布额度已用完，当前无法继续创建排期。请下月额度恢复后再发布，或调整品牌套餐额度。",
                        article.getId(), brandId, platform, account.getId(), null, null, null);
            }
        }
        LocalDateTime nextAttemptAt = nextBrandSafeAttemptAt(brandId, now);
        LocalDateTime plannedPublishAt = plannedPublishAtForQuickSchedule(platform, strategy, nextAttemptAt, now);
        return quickResponse("ready", "READY", "可以创建" + platformLabel(platform) + "平台快速排期",
                article.getId(), brandId, platform, account.getId(), null, plannedPublishAt, nextAttemptAt)
                .withPlan(new QuickScheduleData(candidate, strategy, plannedPublishAt, nextAttemptAt));
    }

    private SelfMediaPublishScheduleCreateResponse createQuickSchedule(QuickSchedulePlan plan,
                                                                      Long operatorId,
                                                                      String idempotencyKeyHeader) {
        QuickScheduleData data = plan.data();
        ValidatedRequest validated = new ValidatedRequest(
                plan.response().getBrandId(),
                List.of(plan.response().getArticleId()),
                List.of(plan.response().getSelfMediaAccountId()),
                plan.response().getPlannedPublishAt(),
                plan.response().getPlannedPublishAt(),
                plan.response().getPlannedPublishAt(),
                plan.response().getPlannedPublishAt(),
                false,
                data.strategy(),
                QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES
        );
        String normalizedPayload = normalizedPayload(validated);
        String requestKey = StringUtils.hasText(idempotencyKeyHeader)
                ? idempotencyKeyHeader.trim()
                : sha256("quick|" + normalizedPayload + "|" + System.nanoTime());
        SelfMediaPublishScheduleRequest requestRow = createRequestRow(validated, operatorId, requestKey,
                sha256(normalizedPayload), normalizedPayload);
        requestMapper.insert(requestRow);

        SelfMediaPublishScheduleCreateResponse response = new SelfMediaPublishScheduleCreateResponse();
        response.setRequestId(requestRow.getId());
        response.setRequestIdempotencyKey(requestKey);
        SelfMediaPublishSchedule inserted = createScheduleRow(requestRow, operatorId, data.candidate(),
                data.plannedPublishAt(), data.nextAttemptAt(), data.strategy(), true);
        if (inserted == null) {
            response.getRejectedItems().add(rejected(plan.response().getArticleId(), plan.response().getSelfMediaAccountId(),
                    plan.response().getPlatform(), "ACTIVE_SCHEDULE_EXISTS", "同一文章、账号和计划时间已存在活跃排期", null));
        } else {
            reserveScheduleQuota(inserted, data.candidate().article().getProjectId(), quotaPrecheck(plan.response().getBrandId()).companyId);
            response.getCreatedSchedules().add(SelfMediaPublishScheduleVO.from(inserted));
        }
        requestRow.setScheduleCount(response.getCreatedSchedules().size());
        requestRow.setStatus("completed");
        requestMapper.updateById(requestRow);
        return response;
    }

    private SelfMediaPublishSchedule cancelSafeReplaceableSchedule(Long scheduleId,
                                                                   Long brandId,
                                                                   String platform,
                                                                   PeriodWindow replacementMonth,
                                                                   LocalDateTime now) {
        if (scheduleId == null) {
            fail("REPLACE_TARGET_NOT_AVAILABLE", "没有可安全替换的排期；即将开始或已进入处理的排期不会被替换");
        }
        SelfMediaPublishSchedule replaceTarget = scheduleMapper.selectById(scheduleId);
        if (replaceTarget == null) {
            fail("REPLACE_TARGET_NOT_AVAILABLE", "可替换排期已开始执行或不存在，请刷新后重试");
        }
        int replaced = scheduleMapper.cancelSafeReplaceablePendingSchedule(
                scheduleId,
                brandId,
                platform,
                replacementMonth.start(),
                replacementMonth.end(),
                now,
                now.plusMinutes(QUICK_DISPATCH_REPLACE_PROTECTION_MINUTES)
        );
        if (replaced <= 0) {
            fail("REPLACE_TARGET_NOT_AVAILABLE", "可替换排期已进入保护窗口或开始处理，请稍后再试");
        }
        refundScheduleQuotaIfPresent(replaceTarget);
        return replaceTarget;
    }

    private QuickSchedulePlan withQuickDispatchTiming(QuickSchedulePlan plan, LocalDateTime now) {
        QuickScheduleData data = plan.data();
        LocalDateTime nextAttemptAt = nextBrandProtectedImmediateAttemptAt(plan.response().getBrandId(), now);
        LocalDateTime plannedPublishAt = plannedPublishAtForQuickSchedule(
                plan.response().getPlatform(),
                data.strategy(),
                nextAttemptAt,
                now
        );
        plan.response().setNextAttemptAt(nextAttemptAt);
        plan.response().setPlannedPublishAt(plannedPublishAt);
        return plan.withPlan(new QuickScheduleData(data.candidate(), data.strategy(), plannedPublishAt, nextAttemptAt));
    }

    private String quickDispatchCreatedMessage(SelfMediaPlatformQuickScheduleResponse response,
                                               SelfMediaPublishSchedule replaced) {
        String prefix = replaced == null ? "已创建平台快速分发排期" : "已安全替换最近一条尚未开始的同平台排期，并创建新的快速分发排期";
        return prefix + "，预计系统处理时间 " + response.getNextAttemptAt() + "，预计发布时间 " + response.getPlannedPublishAt();
    }

    public Page<SelfMediaPublishScheduleVO> pageSchedules(Long brandId,
                                                          String platform,
                                                          String status,
                                                          String failureCode,
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
        wrapper.orderByDesc(SelfMediaPublishSchedule::getPlannedPublishAt)
                .orderByDesc(SelfMediaPublishSchedule::getId);
        if (StringUtils.hasText(platform)) {
            wrapper.eq(SelfMediaPublishSchedule::getPlatform, platform.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SelfMediaPublishSchedule::getStatus, status.trim());
        }
        if (StringUtils.hasText(failureCode)) {
            wrapper.eq(SelfMediaPublishSchedule::getFailureCode, failureCode.trim());
        }
        if (articleId != null) {
            wrapper.eq(SelfMediaPublishSchedule::getArticleId, articleId);
        }
        if (selfMediaAccountId != null) {
            wrapper.eq(SelfMediaPublishSchedule::getSelfMediaAccountId, selfMediaAccountId);
        }

        Page<SelfMediaPublishSchedule> data = scheduleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Page<SelfMediaPublishScheduleVO> result = new Page<>(data.getCurrent(), data.getSize(), data.getTotal());
        List<SelfMediaPublishScheduleVO> records = data.getRecords().stream().map(SelfMediaPublishScheduleVO::from).toList();
        enrichDisplayNames(records);
        enrichAlerts(records);
        result.setRecords(records);
        return result;
    }

    public SelfMediaAutomationOverviewVO automationOverview() {
        currentUserService.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        List<String> activeStatuses = new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES);
        long activeTotal = scheduleMapper.countByStatuses(activeStatuses);
        long dueScheduleExecution = scheduleMapper.countDueByQueue(
                SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION,
                List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING),
                now
        );
        long duePublishCheck = scheduleMapper.countDueByQueue(
                SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK,
                List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                ),
                now
        );
        long runningTotal = scheduleMapper.countByStatuses(LOCAL_AGENT_RUNNING_STATUSES);
        long lockedRunning = scheduleMapper.countLockedByStatuses(LOCAL_AGENT_RUNNING_STATUSES, now);
        long failedTotal = scheduleMapper.countByStatuses(List.of(
                SelfMediaPublishScheduleConstants.STATUS_SCHEDULE_FAILED,
                SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED
        ));
        long manualRequired = scheduleMapper.countByStatuses(List.of(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED));
        long publishUnknown = scheduleMapper.countByStatuses(List.of(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN));

        long activeSessions = localAgentSessionMapper.countActiveSessions(now);
        long onlineAgents = localAgentSessionMapper.countOnlineSessions(now, now.minusMinutes(LOCAL_AGENT_ONLINE_WINDOW_MINUTES));
        long estimatedCapacity = onlineAgents * LOCAL_AGENT_ASSUMED_CAPACITY;
        long waitingForLocalAgent = dueScheduleExecution + duePublishCheck;

        return SelfMediaAutomationOverviewVO.builder()
                .generatedAt(now)
                .queue(SelfMediaAutomationOverviewVO.QueueOverview.builder()
                        .activeTotal(activeTotal)
                        .dueScheduleExecution(dueScheduleExecution)
                        .duePublishCheck(duePublishCheck)
                        .runningTotal(runningTotal)
                        .lockedRunning(lockedRunning)
                        .failedTotal(failedTotal)
                        .manualRequired(manualRequired)
                        .publishUnknown(publishUnknown)
                        .build())
                .localExecution(SelfMediaAutomationOverviewVO.LocalExecutionOverview.builder()
                        .onlineAgents(onlineAgents)
                        .activeSessions(activeSessions)
                        .assumedCapacityPerAgent(LOCAL_AGENT_ASSUMED_CAPACITY)
                        .estimatedCapacity(estimatedCapacity)
                        .runningLoad(runningTotal)
                        .waitingForLocalAgent(waitingForLocalAgent)
                        .capacityStatus(capacityStatus(onlineAgents, estimatedCapacity, runningTotal, waitingForLocalAgent))
                        .message(capacityMessage(onlineAgents, estimatedCapacity, runningTotal, waitingForLocalAgent))
                        .build())
                .statusCounts(statusCounts())
                .platformCounts(platformCounts(now, activeStatuses))
                .failureCodeCounts(failureCodeCounts())
                .platformCapabilities(platformCapabilities())
                .thirdPartySubjectPool(thirdPartySubjectPoolOverview())
                .build();
    }

    public SelfMediaPublishScheduleVO detail(Long id) {
        SelfMediaPublishScheduleVO vo = SelfMediaPublishScheduleVO.from(requireScheduleWithAccess(id));
        enrichDisplayNames(List.of(vo));
        enrichAlerts(List.of(vo));
        return vo;
    }

    private List<SelfMediaAutomationOverviewVO.StatusCount> statusCounts() {
        return scheduleMapper.countGroupedByStatus().stream()
                .map(row -> SelfMediaAutomationOverviewVO.StatusCount.builder()
                        .status(textValue(row.get("name")))
                        .count(longValue(row.get("total")))
                        .build())
                .toList();
    }

    private List<SelfMediaAutomationOverviewVO.PlatformCount> platformCounts(LocalDateTime now, List<String> activeStatuses) {
        return scheduleMapper.countGroupedByPlatform(activeStatuses, now).stream()
                .map(row -> SelfMediaAutomationOverviewVO.PlatformCount.builder()
                        .platform(textValue(row.get("name")))
                        .activeCount(longValue(row.get("active_total")))
                        .failedCount(longValue(row.get("failed_total")))
                        .dueCount(longValue(row.get("due_total")))
                        .build())
                .toList();
    }

    private List<SelfMediaAutomationOverviewVO.FailureCodeCount> failureCodeCounts() {
        return scheduleMapper.countGroupedByFailureCode(12).stream()
                .map(row -> {
                    String code = textValue(row.get("name"));
                    return SelfMediaAutomationOverviewVO.FailureCodeCount.builder()
                            .code(code)
                            .label(SelfMediaPublishFailureCodes.label(code))
                            .retryable(SelfMediaPublishFailureCodes.retryable(code))
                            .actionKey(SelfMediaPublishFailureCodes.actionKey(code))
                            .actionLabel(SelfMediaPublishFailureCodes.actionLabel(code))
                            .actionKind(SelfMediaPublishFailureCodes.actionKind(code))
                            .count(longValue(row.get("total")))
                            .build();
                })
                .toList();
    }

    private List<SelfMediaAutomationOverviewVO.PlatformCapability> platformCapabilities() {
        return scheduleCapabilityService.list().stream()
                .map(item -> {
                    SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                            scheduleCapabilityService.readiness(item.getPlatform(), item.getV1Strategy());
                    boolean requiresLocalAgent = item.getPublishChannel() != null
                            && !"OFFICIAL_API".equalsIgnoreCase(item.getPublishChannel());
                    return SelfMediaAutomationOverviewVO.PlatformCapability.builder()
                            .platform(item.getPlatform())
                            .displayName(firstText(item.getDisplayName(), item.getPlatform()))
                            .publishChannel(item.getPublishChannel())
                            .strategy(item.getV1Strategy())
                            .scheduleReady(readiness.ready())
                            .readinessCode(readiness.code())
                            .readinessMessage(readiness.message())
                            .requiresLocalAgent(requiresLocalAgent)
                            .build();
                })
                .toList();
    }

    private SelfMediaAutomationOverviewVO.ThirdPartySubjectPoolOverview thirdPartySubjectPoolOverview() {
        List<Brand> sources = defaultList(brandMapper.selectThirdPartySourceBrands());
        List<SelfMediaAutomationOverviewVO.ThirdPartySubjectPoolSource> rows = sources.stream()
                .map(this::thirdPartySubjectPoolSource)
                .toList();
        return SelfMediaAutomationOverviewVO.ThirdPartySubjectPoolOverview.builder()
                .sourceTotal(rows.size())
                .readySourceTotal(rows.stream().filter(row -> "ready".equals(row.getStatus())).count())
                .missingCoverageTotal(rows.stream().filter(row -> "missing_coverage".equals(row.getStatus())).count())
                .emptyCandidateTotal(rows.stream().filter(row -> "empty_candidate".equals(row.getStatus())).count())
                .sources(rows.stream().limit(THIRD_PARTY_SOURCE_OVERVIEW_LIMIT).toList())
                .build();
    }

    private SelfMediaAutomationOverviewVO.ThirdPartySubjectPoolSource thirdPartySubjectPoolSource(Brand source) {
        ThirdPartySubjectPoolPreviewResponse preview = thirdPartySubjectRotationService.previewPool(source.getId(), 1, 0);
        String status = "ready";
        String message = "可轮换";
        if (preview.coverableIndustries().isEmpty()) {
            status = "missing_coverage";
            message = "信源未配置覆盖行业";
        } else if (preview.candidateCount() <= 0) {
            status = "empty_candidate";
            message = "暂无可轮换主体";
        }
        String nextCandidate = preview.candidates().isEmpty() ? null : preview.candidates().get(0).brandName();
        return SelfMediaAutomationOverviewVO.ThirdPartySubjectPoolSource.builder()
                .sourceBrandId(source.getId())
                .sourceBrandName(firstText(source.getBrandName(), source.getBrandShortName(), String.valueOf(source.getId())))
                .coverableIndustries(preview.coverableIndustries())
                .candidateCount(preview.candidateCount())
                .excludedCount(preview.excludedCount())
                .nextCandidateBrandName(nextCandidate)
                .status(status)
                .message(message)
                .build();
    }

    private String capacityStatus(long onlineAgents, long estimatedCapacity, long runningLoad, long waitingForLocalAgent) {
        if (onlineAgents <= 0 && waitingForLocalAgent > 0) {
            return "blocked";
        }
        if (estimatedCapacity <= runningLoad && waitingForLocalAgent > 0) {
            return "saturated";
        }
        if (waitingForLocalAgent > estimatedCapacity * 2) {
            return "pressure";
        }
        return "healthy";
    }

    private String capacityMessage(long onlineAgents, long estimatedCapacity, long runningLoad, long waitingForLocalAgent) {
        if (onlineAgents <= 0 && waitingForLocalAgent > 0) {
            return "当前没有在线本地助手，但存在待领取任务";
        }
        if (estimatedCapacity <= runningLoad && waitingForLocalAgent > 0) {
            return "本地助手执行容量已被占满，待领取任务会排队";
        }
        if (waitingForLocalAgent > estimatedCapacity * 2) {
            return "待领取任务明显高于当前本地执行容量";
        }
        return "本地执行容量与待处理任务基本匹配";
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Transactional
    public SelfMediaPublishScheduleVO claimNext(String queueKind, int lockMinutes) {
        SelfMediaPublishSchedule claimed = claimNextRow(queueKind, lockMinutes, null, null, null);
        return claimed == null ? null : SelfMediaPublishScheduleVO.from(claimed);
    }

    @Transactional
    public SelfMediaPublishScheduleVO claimNext(String queueKind, int lockMinutes, Set<String> platforms) {
        Set<String> normalizedPlatforms = normalizePlatforms(platforms);
        if (normalizedPlatforms.isEmpty()) {
            return null;
        }
        SelfMediaPublishSchedule claimed = claimNextRow(queueKind, lockMinutes, null, null, normalizedPlatforms);
        return claimed == null ? null : SelfMediaPublishScheduleVO.from(claimed);
    }

    public List<String> localAgentAutomationPlatforms() {
        return platformsByChannel(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION).stream()
                .filter(platform -> scheduleCapabilityService.readiness(platform).ready())
                .sorted()
                .toList();
    }

    @Transactional
    public SelfMediaPublishScheduleVO heartbeatLocalAgentSchedule(Long operatorId, Long scheduleId, int lockMinutes) {
        if (operatorId == null || operatorId <= 0) {
            fail("INVALID_OPERATOR", "operatorId must be a positive number");
        }
        if (scheduleId == null || scheduleId <= 0) {
            fail("INVALID_SCHEDULE", "scheduleId must be a positive number");
        }
        SelfMediaPublishSchedule row = requireSchedule(scheduleId);
        if (!operatorId.equals(row.getCreatedBy())) {
            fail("SCHEDULE_OPERATOR_MISMATCH", "当前本地助手不能续租该排期");
        }
        String status = normalize(row.getStatus());
        if (!LOCAL_AGENT_RUNNING_STATUSES.contains(status)) {
            fail("SCHEDULE_STATUS_NOT_RUNNING", "当前排期不处于本地助手执行状态");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(Math.max(lockMinutes, 1));
        int updated = scheduleMapper.renewLocalAgentLock(
                scheduleId,
                operatorId,
                LOCAL_AGENT_RUNNING_STATUSES,
                lockedUntil,
                now
        );
        if (updated <= 0) {
            fail("SCHEDULE_LOCK_RENEW_FAILED", "排期锁续租失败");
        }
        boolean environmentRenewed = environmentLockService.renew(
                row.getBrowserEnvironmentId(),
                row.getId(),
                lockedUntil,
                now
        );
        if (!environmentRenewed) {
            environmentLockService.tryAcquire(row.getBrowserEnvironmentId(), row.getId(), lockedUntil, now);
        }
        SelfMediaPublishSchedule latest = scheduleMapper.selectById(scheduleId);
        return SelfMediaPublishScheduleVO.from(latest == null ? row : latest);
    }

    @Transactional
    public int recoverTimedOutLocalAgentSchedules(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int recovered = 0;
        List<SelfMediaPublishSchedule> rows = scheduleMapper.selectTimedOutRunning(
                LOCAL_AGENT_RUNNING_STATUSES,
                now,
                Math.max(limit, 1)
        );
        for (SelfMediaPublishSchedule row : rows) {
            if (recoverTimedOutLocalAgentSchedule(row, now)) {
                recovered++;
            }
        }
        return recovered;
    }

    @Transactional(noRollbackFor = BizException.class)
    public ClaimedScheduleTask claimNextTaskForLocalAgent(Long operatorId, String platform, int lockMinutes) {
        if (operatorId == null || operatorId <= 0) {
            fail("INVALID_OPERATOR", "operatorId must be a positive number");
        }
        Set<String> allowedPlatforms = platformsByChannel(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION);
        if (allowedPlatforms.isEmpty()) {
            return null;
        }
        String normalizedPlatform = normalizePublishPlatform(platform);
        if (normalizedPlatform != null && !allowedPlatforms.contains(normalizedPlatform)) {
            return null;
        }
        if (!hasAvailableLocalAgentCapacity(operatorId)) {
            return null;
        }
        SelfMediaPublishSchedule claimed = claimNextRow(
                SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION,
                lockMinutes,
                operatorId,
                normalizedPlatform,
                allowedPlatforms
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
        } catch (BizException ex) {
            markClaimFailed(
                    claimed.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING,
                    distributionTaskPrepareFailureCode(ex),
                    trimError(ex.getMessage()),
                    null,
                    null
            );
            throw ex;
        } catch (RuntimeException ex) {
            String message = trimError(ex.getMessage());
            markClaimFailed(
                    claimed.getId(),
                    SelfMediaPublishScheduleConstants.STATUS_FILLING,
                    "DISTRIBUTION_TASK_PREPARE_FAILED",
                    message,
                    null,
                    null
            );
            throw new BizException(500, "Prepare self-media distribution task failed: " + message, ex);
        }
    }

    @Transactional
    public SelfMediaPublishScheduleVO claimNextPublishCheckForLocalAgent(Long operatorId, String platform, int lockMinutes) {
        if (operatorId == null || operatorId <= 0) {
            fail("INVALID_OPERATOR", "operatorId must be a positive number");
        }
        Set<String> allowedPlatforms = platformsByChannel(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION);
        if (allowedPlatforms.isEmpty()) {
            return null;
        }
        String normalizedPlatform = normalizePublishPlatform(platform);
        if (normalizedPlatform != null && !allowedPlatforms.contains(normalizedPlatform)) {
            return null;
        }
        if (!hasAvailableLocalAgentCapacity(operatorId)) {
            return null;
        }
        SelfMediaPublishSchedule claimed = claimNextRow(
                SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK,
                List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                ),
                SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT,
                lockMinutes,
                operatorId,
                normalizedPlatform,
                allowedPlatforms
        );
        return claimed == null ? null : SelfMediaPublishScheduleVO.from(claimed);
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
            confirmDistributionQuotaIfPresent(row);
            return SelfMediaPublishScheduleVO.from(row);
        }
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markDistributionTaskPublishedConfirmed(Long distributionTaskId,
                                                                            String platformPublishedUrl,
                                                                            String diagnosticsJson) {
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
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)) {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
            row.setPublishedConfirmedAt(LocalDateTime.now());
            row.setPlatformPublishedUrl(trimToNull(platformPublishedUrl));
            row.setLockedUntil(null);
            row.setNextAttemptAt(null);
            row.setFailureCode(null);
            row.setFailureMessage(null);
            row.setDiagnosticsJson(trimToNull(diagnosticsJson));
            scheduleMapper.updateById(row);
            confirmScheduleQuotaIfPresent(row);
            confirmDistributionQuotaIfPresent(row);
            environmentLockService.release(row.getId());
            reconcileAlerts(row);
            return SelfMediaPublishScheduleVO.from(row);
        }
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markLocalAgentExecutionFilled(Long id, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)) {
            return markClaimedFilledVerified(row.getId(), diagnosticsJson);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        fail("SCHEDULE_STATUS_NOT_FILLING", "当前排期未处于填充执行中");
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markLocalAgentExecutionScheduled(Long id, String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
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
        if (SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        fail("SCHEDULE_STATUS_NOT_SCHEDULABLE", "当前排期状态不允许确认平台定时成功");
        return null;
    }

    @Transactional
    public SelfMediaPublishScheduleVO markLocalAgentExecutionPublishedConfirmed(Long id,
                                                                               String platformPublishedUrl,
                                                                               String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        String status = normalize(row.getStatus());
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULING.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_SCHEDULED.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)
                || SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN.equals(status)) {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
            row.setPublishedConfirmedAt(LocalDateTime.now());
            row.setPlatformPublishedUrl(trimToNull(platformPublishedUrl));
            row.setLockedUntil(null);
            row.setNextAttemptAt(null);
            row.setFailureCode(null);
            row.setFailureMessage(null);
            row.setDiagnosticsJson(trimToNull(diagnosticsJson));
            scheduleMapper.updateById(row);
            confirmScheduleQuotaIfPresent(row);
            confirmDistributionQuotaIfPresent(row);
            environmentLockService.release(row.getId());
            reconcileAlerts(row);
            return SelfMediaPublishScheduleVO.from(row);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED.equals(status)) {
            return SelfMediaPublishScheduleVO.from(row);
        }
        fail("SCHEDULE_STATUS_NOT_PUBLISH_CONFIRMABLE", "当前排期状态不允许确认已发布");
        return null;
    }

    private boolean hasAvailableLocalAgentCapacity(Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        long onlineSessions = localAgentSessionMapper.countOnlineSessionsByOperator(
                operatorId,
                now,
                now.minusMinutes(LOCAL_AGENT_ONLINE_WINDOW_MINUTES)
        );
        long estimatedCapacity = onlineSessions * LOCAL_AGENT_ASSUMED_CAPACITY;
        if (estimatedCapacity <= 0) {
            return false;
        }
        long runningLoad = scheduleMapper.countLockedByOperatorAndStatuses(operatorId, LOCAL_AGENT_RUNNING_STATUSES, now);
        return runningLoad < estimatedCapacity;
    }

    private SelfMediaPublishSchedule claimNextRow(String queueKind,
                                                  int lockMinutes,
                                                  Long operatorId,
                                                  String platform) {
        return claimNextRow(queueKind, lockMinutes, operatorId, platform, null);
    }

    private SelfMediaPublishSchedule claimNextRow(String queueKind,
                                                  int lockMinutes,
                                                  Long operatorId,
                                                  String platform,
                                                  Set<String> allowedPlatforms) {
        QueueClaimProfile profile = requireClaimProfile(queueKind);
        return claimNextRow(queueKind, profile.expectedStatuses(), profile.targetStatus(), lockMinutes,
                operatorId, platform, allowedPlatforms);
    }

    private SelfMediaPublishSchedule claimNextRow(String queueKind,
                                                  List<String> expectedStatuses,
                                                  String targetStatus,
                                                  int lockMinutes,
                                                  Long operatorId,
                                                  String platform) {
        return claimNextRow(queueKind, expectedStatuses, targetStatus, lockMinutes, operatorId, platform, null);
    }

    private SelfMediaPublishSchedule claimNextRow(String queueKind,
                                                  List<String> expectedStatuses,
                                                  String targetStatus,
                                                  int lockMinutes,
                                                  Long operatorId,
                                                  String platform,
                                                  Set<String> allowedPlatforms) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plusMinutes(Math.max(lockMinutes, 1));
        Set<String> normalizedAllowedPlatforms = normalizePlatforms(allowedPlatforms);
        List<SelfMediaPublishSchedule> candidates = selectDueQueueCandidates(
                queueKind,
                expectedStatuses,
                now,
                operatorId,
                platform,
                normalizedAllowedPlatforms
        );
        for (SelfMediaPublishSchedule candidate : candidates) {
            if (operatorId != null && postponeLocalAgentClaimOutsideBusinessWindow(candidate, now)) {
                continue;
            }
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
                    expectedStatuses,
                    targetStatus,
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

    private boolean postponeLocalAgentClaimOutsideBusinessWindow(SelfMediaPublishSchedule row, LocalDateTime now) {
        if (isManualQuickDispatchSchedule(row)) {
            return false;
        }
        LocalDateTime nextWindow = clampToBusinessAttemptWindow(now);
        if (nextWindow == null || !nextWindow.isAfter(now.withSecond(0).withNano(0))) {
            return false;
        }
        row.setNextAttemptAt(nextBrandSafeAttemptAt(row.getBrandId(), nextWindow, row.getId()));
        row.setLockedUntil(null);
        row.setUpdatedAt(now);
        scheduleMapper.updateById(row);
        return true;
    }

    private boolean isManualQuickDispatchSchedule(SelfMediaPublishSchedule row) {
        return row != null
                && StringUtils.hasText(row.getRequestIdempotencyKey())
                && row.getRequestIdempotencyKey().startsWith("platform-quick-dispatch-");
    }

    private boolean recoverTimedOutLocalAgentSchedule(SelfMediaPublishSchedule row, LocalDateTime now) {
        if (row == null || row.getId() == null || row.getLockedUntil() == null || row.getLockedUntil().isAfter(now)) {
            return false;
        }
        SelfMediaPublishSchedule latest = scheduleMapper.selectById(row.getId());
        if (latest == null || latest.getLockedUntil() == null || latest.getLockedUntil().isAfter(now)) {
            return false;
        }
        String status = normalize(latest.getStatus());
        if (!LOCAL_AGENT_RUNNING_STATUSES.contains(status)) {
            return false;
        }
        if (SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT.equals(status)) {
            recoverTimedOutPublishCheck(latest);
        } else {
            recoverTimedOutScheduleExecution(latest);
        }
        environmentLockService.release(latest.getId());
        reconcileAlerts(latest);
        return true;
    }

    private void recoverTimedOutScheduleExecution(SelfMediaPublishSchedule row) {
        String failureCode = "LOCAL_AGENT_HEARTBEAT_TIMEOUT";
        LocalDateTime nextAttemptAt = nextScheduleExecutionRetryAt(row, failureCode);
        row.setLockedUntil(null);
        row.setFailureCode(failureCode);
        row.setFailureMessage("本地助手执行心跳超时，系统已释放浏览器环境锁");
        row.setDiagnosticsJson(diagnosticsJson("recoveredAt", LocalDateTime.now(), "reason", failureCode));
        if (canRetry(row, nextAttemptAt)) {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
            row.setNextAttemptAt(nextAttemptAt);
        } else {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
            row.setNextAttemptAt(null);
        }
        scheduleMapper.updateById(row);
        refundDistributionQuotaIfPresent(row);
        if (SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(normalize(row.getStatus()))) {
            refundScheduleQuotaIfPresent(row);
        }
    }

    private void recoverTimedOutPublishCheck(SelfMediaPublishSchedule row) {
        int maxAttempts = Math.max(
                row.getMaxAttempts() == null ? PUBLISH_CHECK_TOTAL_ATTEMPTS : row.getMaxAttempts(),
                PUBLISH_CHECK_TOTAL_ATTEMPTS
        );
        LocalDateTime nextAttemptAt = nextPublishCheckRetryAt(row, maxAttempts);
        row.setMaxAttempts(maxAttempts);
        row.setLockedUntil(null);
        row.setDiagnosticsJson(diagnosticsJson("recoveredAt", LocalDateTime.now(), "reason", "PUBLISH_RESULT_CHECK_HEARTBEAT_TIMEOUT"));
        if (nextAttemptAt != null) {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
            row.setNextAttemptAt(nextAttemptAt);
            row.setFailureCode("PUBLISH_RESULT_NOT_MATCHED_RETRYING");
            row.setFailureMessage("发布结果回查心跳超时，等待系统自动复查");
        } else {
            row.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
            row.setNextAttemptAt(null);
            row.setFailureCode("PUBLISH_RESULT_NOT_MATCHED");
            row.setFailureMessage("发布结果回查心跳超时且已达到最大复查次数，请人工确认");
        }
        scheduleMapper.updateById(row);
    }

    private List<SelfMediaPublishSchedule> selectDueQueueCandidates(String queueKind,
                                                                    List<String> expectedStatuses,
                                                                    LocalDateTime now,
                                                                    Long operatorId,
                                                                    String platform,
                                                                    Set<String> allowedPlatforms) {
        if (operatorId == null) {
            if (allowedPlatforms == null || allowedPlatforms.isEmpty()) {
                return scheduleMapper.selectDueQueueCandidates(queueKind, expectedStatuses, now, DEFAULT_CLAIM_LIMIT);
            }
            return scheduleMapper.selectDueQueueCandidatesByPlatforms(
                    queueKind,
                    expectedStatuses,
                    now,
                    DEFAULT_CLAIM_LIMIT,
                    allowedPlatforms
            );
        }
        return scheduleMapper.selectDueQueueCandidatesForOperator(
                queueKind,
                expectedStatuses,
                now,
                DEFAULT_CLAIM_LIMIT,
                operatorId,
                platform,
                allowedPlatforms
        );
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
            if (SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(normalize(row.getStatus()))) {
                refundScheduleQuotaIfPresent(row);
            }
            refundDistributionQuotaIfPresent(row);
            environmentLockService.release(row.getId());
            reconcileAlerts(row);
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
        Map<String, Object> automationOptions = scheduleCapabilityService.automationOptions(schedule.getPlatform());
        if (!automationOptions.isEmpty()) {
            platformOptions.put(normalize(schedule.getPlatform()), automationOptions);
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
        PlatformScheduleVerification verification = parsePlatformScheduleVerification(diagnosticsJson);
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED);
        row.setPlatformScheduleId(trimToNull(platformScheduleId));
        if (verification.platformScheduledAt() != null) {
            row.setPlatformScheduledAt(verification.platformScheduledAt());
        }
        if (verification.platformScheduleId() != null) {
            row.setPlatformScheduleId(verification.platformScheduleId());
        }
        row.setScheduledAt(LocalDateTime.now());
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        row.setNextAttemptAt(resolvePublishResultCheckAttemptTime(row));
        row.setLockedUntil(null);
        row.setFailureCode(null);
        row.setFailureMessage(null);
        row.setDiagnosticsJson(trimToNull(diagnosticsJson));
        scheduleMapper.updateById(row);
        confirmScheduleQuotaIfPresent(row);
        confirmDistributionQuotaIfPresent(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    private PlatformScheduleVerification parsePlatformScheduleVerification(String diagnosticsJson) {
        if (!StringUtils.hasText(diagnosticsJson)) {
            return new PlatformScheduleVerification(null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(diagnosticsJson);
            JsonNode verification = root.path("fillResult").path("publishOptions").path("publishVerification");
            if (verification.isMissingNode()) {
                return new PlatformScheduleVerification(null, null);
            }
            LocalDateTime platformScheduledAt = parseLocalDateTime(firstText(
                    verification.path("platformScheduledAt").asText(null),
                    verification.path("scheduledAtText").asText(null)
            ));
            String platformScheduleId = trimToNull(firstText(
                    verification.path("platformScheduleId").asText(null),
                    verification.path("platformPublishId").asText(null)
            ));
            return new PlatformScheduleVerification(platformScheduledAt, platformScheduleId);
        } catch (JsonProcessingException ignored) {
            return new PlatformScheduleVerification(null, null);
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() >= 16) {
            text = text.substring(0, 16);
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
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

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record PlatformScheduleVerification(
            LocalDateTime platformScheduledAt,
            String platformScheduleId
    ) {
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
        if (isPendingPlatformScheduledDiagnostics(diagnosticsJson)) {
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
            row.setNextAttemptAt(nextPendingPlatformScheduleCheckAt(row));
            row.setFailureCode("PLATFORM_SCHEDULED_WAITING");
            row.setFailureMessage("平台已定时，等待发布时间后复查");
            scheduleMapper.updateById(row);
            environmentLockService.release(row.getId());
            reconcileAlerts(row);
            return SelfMediaPublishScheduleVO.from(row);
        }
        int publishCheckMaxAttempts = Math.max(row.getMaxAttempts() == null ? 0 : row.getMaxAttempts(),
                PUBLISH_CHECK_TOTAL_ATTEMPTS);
        LocalDateTime retryAt = nextPublishCheckRetryAt(row, publishCheckMaxAttempts);
        if (retryAt != null) {
            row.setMaxAttempts(publishCheckMaxAttempts);
            row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
            row.setNextAttemptAt(retryAt);
            row.setFailureCode("PUBLISH_RESULT_NOT_MATCHED_RETRYING");
            row.setFailureMessage("平台作品暂未匹配到，系统将延迟复查");
        } else {
            row.setNextAttemptAt(null);
            row.setFailureCode("PUBLISH_RESULT_NOT_MATCHED");
            row.setFailureMessage("多次复查后仍未匹配到平台作品，请人工确认");
        }
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    private boolean isPendingPlatformScheduledDiagnostics(String diagnosticsJson) {
        if (!StringUtils.hasText(diagnosticsJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(diagnosticsJson);
            return root.path("pendingScheduled").asBoolean(false)
                    || "platform schedule time not due".equals(root.path("reason").asText(null));
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private LocalDateTime nextPendingPlatformScheduleCheckAt(SelfMediaPublishSchedule row) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime platformScheduledAt = row.getPlatformScheduledAt();
        LocalDateTime candidate;
        if (platformScheduledAt != null && platformScheduledAt.isAfter(now)) {
            candidate = platformScheduledAt.plusMinutes(3);
        } else {
            candidate = now.plusMinutes(5);
        }
        return nextBrandSafeAttemptAt(row.getBrandId(), candidate, row.getId());
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
        markArticlePublished(row.getArticleId());
        confirmDistributionQuotaIfPresent(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
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
        releaseArticleIfNoActiveSchedule(row);
        refundScheduleQuotaIfPresent(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
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
        if (SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(normalize(expectedRunningStatus))) {
            refundDistributionQuotaIfPresent(row);
        }
        if (SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED.equals(normalize(row.getStatus()))) {
            refundScheduleQuotaIfPresent(row);
        }
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markLocalAgentExecutionFailed(Long id,
                                                                    String failureCode,
                                                                    String failureMessage,
                                                                    String diagnosticsJson) {
        SelfMediaPublishSchedule row = requireSchedule(id);
        if (!SelfMediaPublishScheduleConstants.STATUS_FILLING.equals(normalize(row.getStatus()))) {
            environmentLockService.release(row.getId());
            return SelfMediaPublishScheduleVO.from(row);
        }
        return markClaimFailed(
                id,
                SelfMediaPublishScheduleConstants.STATUS_FILLING,
                failureCode,
                failureMessage,
                diagnosticsJson,
                nextScheduleExecutionRetryAt(row, failureCode)
        );
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
        if (!PLATFORM_SUBMITTED_STATUSES.contains(status)) {
            refundScheduleQuotaIfPresent(row);
            refundDistributionQuotaIfPresent(row);
            releaseArticleIfNoActiveSchedule(row);
        }
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
        releaseArticleIfNoActiveSchedule(row);
        refundScheduleQuotaIfPresent(row);
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
        markArticlePublished(row.getArticleId());
        confirmScheduleQuotaIfPresent(row);
        confirmDistributionQuotaIfPresent(row);
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
        releaseArticleIfNoActiveSchedule(row);
        refundScheduleQuotaIfPresent(row);
        environmentLockService.release(row.getId());
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO recheckPublishResult(Long id) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        if (!PUBLISH_RESULT_RECHECKABLE_STATUSES.contains(normalize(row.getStatus()))) {
            fail("SCHEDULE_STATUS_NOT_RECHECKABLE", "当前排期状态不允许重新校验发布结果");
        }
        queuePublishResultRecheck(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO retryNow(Long id) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        String status = normalize(row.getStatus());
        String queueKind = normalize(row.getQueueKind());
        if (SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK.equals(queueKind)
                || PUBLISH_RESULT_RECHECKABLE_STATUSES.contains(status)
                && !SCHEDULE_EXECUTION_RETRYABLE_STATUSES.contains(status)) {
            if (!PUBLISH_RESULT_RECHECKABLE_STATUSES.contains(status)) {
                fail("SCHEDULE_STATUS_NOT_RETRYABLE", "当前排期状态不允许立即重试");
            }
            queuePublishResultRecheck(row);
            return SelfMediaPublishScheduleVO.from(row);
        }
        if (!SCHEDULE_EXECUTION_RETRYABLE_STATUSES.contains(status)) {
            fail("SCHEDULE_STATUS_NOT_RETRYABLE", "当前排期状态不允许立即重试");
        }
        if (isStillLocked(row)) {
            fail("SCHEDULE_LOCK_STILL_ACTIVE", "当前排期仍被本地助手锁定，请等待心跳超时或先转人工处理");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setLockedUntil(null);
        row.setFailureCode("MANUAL_RETRY_REQUESTED");
        row.setFailureMessage("已人工触发立即重试");
        row.setMaxAttempts(Math.max(row.getMaxAttempts() == null ? 0 : row.getMaxAttempts(),
                (row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1));
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    @Transactional
    public SelfMediaPublishScheduleVO markManualRequired(Long id, String reason) {
        SelfMediaPublishSchedule row = requireScheduleWithAccess(id);
        String status = normalize(row.getStatus());
        if (!MANUAL_MARKABLE_STATUSES.contains(status)) {
            fail("SCHEDULE_STATUS_NOT_MANUAL_MARKABLE", "当前排期状态不允许转人工处理");
        }
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setLockedUntil(null);
        row.setNextAttemptAt(null);
        row.setFailureCode("MANUAL_REQUIRED_BY_OPERATOR");
        row.setFailureMessage(trimToNull(reason));
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
        return SelfMediaPublishScheduleVO.from(row);
    }

    private void queuePublishResultRecheck(SelfMediaPublishSchedule row) {
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        row.setNextAttemptAt(LocalDateTime.now());
        row.setLockedUntil(null);
        row.setFailureCode("PUBLISH_RESULT_RECHECK_REQUESTED");
        row.setFailureMessage("已人工触发重新校验发布结果");
        row.setMaxAttempts(Math.max(row.getMaxAttempts() == null ? 0 : row.getMaxAttempts(),
                (row.getAttemptCount() == null ? 0 : row.getAttemptCount()) + 1));
        touch(row);
        scheduleMapper.updateById(row);
        environmentLockService.release(row.getId());
        reconcileAlerts(row);
    }

    private boolean isStillLocked(SelfMediaPublishSchedule row) {
        return row.getLockedUntil() != null && row.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private ValidatedRequest validateRequest(SelfMediaPublishScheduleCreateRequest request) {
        if (request == null || request.getBrandId() == null || request.getBrandId() <= 0) {
            fail("INVALID_BRAND", "brandId must be a positive number");
        }
        List<Long> articleIds = distinctPositive(request.getArticleIds(), "articleIds");
        List<Long> accountIds = distinctPositive(request.getSelfMediaAccountIds(), "selfMediaAccountIds");
        LocalDateTime windowStart = request.getWindowStart();
        LocalDateTime windowEnd = request.getWindowEnd();
        if (windowStart == null || windowEnd == null || windowEnd.isBefore(windowStart)) {
            fail("INVALID_SCHEDULE_WINDOW", "排期时间窗口无效");
        }
        boolean hasExplicitExecutionWindow = request.getExecutionWindowStart() != null
                || request.getExecutionWindowEnd() != null;
        LocalDateTime executionWindowStart = request.getExecutionWindowStart() == null
                ? windowStart
                : request.getExecutionWindowStart();
        LocalDateTime executionWindowEnd = request.getExecutionWindowEnd() == null
                ? (hasExplicitExecutionWindow ? executionWindowStart : windowEnd)
                : request.getExecutionWindowEnd();
        if (executionWindowEnd.isBefore(executionWindowStart)) {
            fail("INVALID_EXECUTION_WINDOW", "执行填充时间窗口无效");
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
        if (!List.of(
                SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE,
                SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH
        ).contains(strategy)) {
            fail("INVALID_SCHEDULE_STRATEGY", "未知排期策略");
        }
        int interval = request.getMinIntervalMinutes() == null
                ? DEFAULT_INTERVAL_MINUTES
                : request.getMinIntervalMinutes();
        if (interval <= 0) {
            fail("INVALID_INTERVAL", "最小错峰间隔必须大于 0");
        }
        return new ValidatedRequest(request.getBrandId(), articleIds, accountIds, windowStart, windowEnd,
                executionWindowStart, executionWindowEnd, hasExplicitExecutionWindow, strategy, interval);
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
        return scheduleAdapterRouter.rules(platform, strategy).maxAttempts();
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

    private Candidate validateCandidate(Long brandId,
                                        ArticleDraft article,
                                        Long articleId,
                                        Long accountId,
                                        String strategy) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(accountId);
        String platform = account == null ? null : account.getPlatform();
        if (article == null) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_NOT_FOUND", "文章不存在", null));
        }
        String articleStatus = normalize(article.getStatus());
        if (LOCKED_ARTICLE_STATUS.contains(articleStatus)) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_ALREADY_PUBLISHED", "文章已发布或已分发，不能重复创建自媒体排期", null));
        }
        if (hasActiveSelfMediaSchedule(articleId, null)) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_SELF_MEDIA_SCHEDULE_ACTIVE", "文章已有自媒体排期正在处理，不能重复创建分发任务", null));
        }
        if (!ACTIVE_ARTICLE_STATUS.contains(articleStatus)) {
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
        if ("baijiahao".equals(normalize(platform)) && !StringUtils.hasText(account.getPlatformAccountId())) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "BAIJIAHAO_APP_ID_REQUIRED",
                    "百家号账号缺少百家号 ID / app_id，请在品牌详情中补充后再创建排期",
                    "品牌详情 > 自媒体账号 > 百家号 ID"));
        }
        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness =
                scheduleCapabilityService.readiness(platform, strategy);
        if (!readiness.ready()) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    readiness.code(), readiness.message(), "全自动排期 > 平台能力验证"));
        }
        boolean requiresCoverUpload = scheduleAdapterRouter.contract(platform)
                .map(contract -> contract.requiresCoverUpload())
                .orElse(false);
        if (requiresCoverUpload && !StringUtils.hasText(article.getCoverImageUrl())) {
            return Candidate.rejected(rejected(articleId, accountId, platform,
                    "ARTICLE_COVER_REQUIRED", "该平台发布需要文章封面，请先为文章选择封面", "文章详情 > 文章封面"));
        }
        BrowserEnvironmentAccount binding;
        try {
            binding = browserEnvironmentService.validateForTaskCreation(account, false);
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
                                                       LocalDateTime executionAt,
                                                       String strategy) {
        return createScheduleRow(requestRow, operatorId, candidate, plannedAt, executionAt, strategy, false);
    }

    private SelfMediaPublishSchedule createScheduleRow(SelfMediaPublishScheduleRequest requestRow,
                                                       Long operatorId,
                                                       Candidate candidate,
                                                       LocalDateTime plannedAt,
                                                       LocalDateTime executionAt,
                                                       String strategy,
                                                       boolean useExactExecutionAt) {
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
        row.setPublishCheckTitle(truncate(trimToNull(candidate.article().getTitle()), 255));
        row.setPublishCheckCoverUrl(truncate(trimToNull(candidate.article().getCoverImageUrl()), 1000));
        row.setPublishCheckLocationName(truncate(resolvePublishCheckLocationName(requestRow.getBrandId()), 128));
        row.setPublishCheckFingerprint(publishCheckFingerprint(row));
        row.setAttemptCount(0);
        row.setMaxAttempts(resolveMaxAttempts(candidate.account().getPlatform(), strategy));
        row.setNextAttemptAt(resolveScheduleExecutionAttemptTime(
                requestRow.getBrandId(),
                plannedAt,
                executionAt,
                candidate.account().getPlatform(),
                strategy,
                useExactExecutionAt
        ));
        row.setCreatedBy(operatorId);
        row.setUpdatedBy(operatorId);

        try {
            scheduleMapper.insert(row);
            markArticleDistributing(row.getArticleId());
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

    private LocalDateTime resolveScheduleExecutionAttemptTime(Long brandId,
                                                              LocalDateTime plannedAt,
                                                              LocalDateTime executionAt,
                                                              String platform,
                                                              String strategy) {
        return resolveScheduleExecutionAttemptTime(brandId, plannedAt, executionAt, platform, strategy, false);
    }

    private LocalDateTime resolveScheduleExecutionAttemptTime(Long brandId,
                                                              LocalDateTime plannedAt,
                                                              LocalDateTime executionAt,
                                                              String platform,
                                                              String strategy,
                                                              boolean useExactExecutionAt) {
        if (useExactExecutionAt && executionAt != null) {
            return executionAt;
        }
        if (executionAt != null) {
            return nextBrandSafeAttemptAt(brandId, executionAt, null);
        }
        int leadMinutes = resolveScheduleExecutionLeadMinutes(platform, strategy);
        LocalDateTime fillAt = plannedAt.minusMinutes(leadMinutes);
        LocalDateTime now = LocalDateTime.now();
        return nextBrandSafeAttemptAt(brandId, fillAt.isBefore(now) ? now : fillAt, null);
    }

    private int resolveScheduleExecutionLeadMinutes(String platform, String strategy) {
        return scheduleAdapterRouter.rules(platform, strategy).fillLeadMinutes();
    }

    private LocalDateTime resolvePublishResultCheckAttemptTime(SelfMediaPublishSchedule row) {
        LocalDateTime target = row.getPlatformScheduledAt() != null
                ? row.getPlatformScheduledAt()
                : row.getPlannedPublishAt();
        return nextBrandSafeAttemptAt(row.getBrandId(), target == null ? LocalDateTime.now() : target, row.getId());
    }

    private LocalDateTime nextPublishCheckRetryAt(SelfMediaPublishSchedule row, int maxAttempts) {
        int attempts = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        if (attempts >= maxAttempts) {
            return null;
        }
        int retryIndex = Math.max(0, attempts - 2);
        int delayMinutes = PUBLISH_CHECK_RETRY_DELAYS_MINUTES[
                Math.min(retryIndex, PUBLISH_CHECK_RETRY_DELAYS_MINUTES.length - 1)
        ];
        return nextBrandSafeAttemptAt(row.getBrandId(), LocalDateTime.now().plusMinutes(delayMinutes), row.getId());
    }

    private void reconcileAlerts(SelfMediaPublishSchedule row) {
        if (row != null && row.getId() != null) {
            alertService.reconcile(row, LocalDateTime.now());
        }
    }

    private LocalDateTime nextScheduleExecutionRetryAt(SelfMediaPublishSchedule row, String failureCode) {
        if (!isScheduleExecutionRetryableFailure(failureCode)) {
            return null;
        }
        int attempts = row == null || row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        int retryIndex = Math.max(0, attempts - 1);
        int delayMinutes = SCHEDULE_EXECUTION_RETRY_DELAYS_MINUTES[
                Math.min(retryIndex, SCHEDULE_EXECUTION_RETRY_DELAYS_MINUTES.length - 1)
        ];
        return nextBrandSafeAttemptAt(row.getBrandId(), LocalDateTime.now().plusMinutes(delayMinutes), row.getId());
    }

    private LocalDateTime clampToBusinessAttemptWindow(LocalDateTime candidate) {
        if (candidate == null) {
            return null;
        }
        LocalDateTime cursor = candidate.withSecond(0).withNano(0);
        YearMonth month = YearMonth.from(cursor);
        for (int i = 0; i < 3; i++) {
            List<BusinessCalendarService.BusinessDay> days;
            try {
                days = businessCalendarService.publishDays(month, false);
            } catch (RuntimeException ex) {
                return cursor;
            }
            for (BusinessCalendarService.BusinessDay day : days) {
                for (BusinessCalendarService.PublishWindow window : day.windows()) {
                    LocalDateTime start = day.date().atTime(window.start());
                    LocalDateTime end = day.date().atTime(window.end());
                    if (cursor.isAfter(end)) {
                        continue;
                    }
                    if (cursor.isBefore(start)) {
                        return start;
                    }
                    return cursor;
                }
            }
            month = month.plusMonths(1);
            cursor = month.atDay(1).atStartOfDay();
        }
        return candidate.withSecond(0).withNano(0);
    }

    private boolean isScheduleExecutionRetryableFailure(String failureCode) {
        return SelfMediaPublishFailureCodes.isScheduleExecutionRetryable(failureCode);
    }

    private void confirmDistributionQuotaIfPresent(SelfMediaPublishSchedule row) {
        if (row != null && row.getDistributionTaskId() != null) {
            companyChannelQuotaService.confirmDistribution(row.getDistributionTaskId());
        }
    }

    private void markArticleDistributing(Long articleId) {
        ArticleDraft article = articleId == null ? null : articleDraftMapper.selectById(articleId);
        if (article == null) {
            return;
        }
        String status = normalize(article.getStatus());
        if (!ACTIVE_ARTICLE_STATUS.contains(status)) {
            return;
        }
        article.setStatus("distributing");
        articleDraftMapper.updateById(article);
    }

    private void markArticlePublished(Long articleId) {
        ArticleDraft article = articleId == null ? null : articleDraftMapper.selectById(articleId);
        if (article == null) {
            return;
        }
        article.setStatus("published");
        articleDraftMapper.updateById(article);
    }

    private void releaseArticleIfNoActiveSchedule(SelfMediaPublishSchedule row) {
        if (row == null || row.getArticleId() == null) {
            return;
        }
        if (hasActiveSelfMediaSchedule(row.getArticleId(), row.getId())) {
            return;
        }
        ArticleDraft article = articleDraftMapper.selectById(row.getArticleId());
        if (article == null || !"distributing".equals(normalize(article.getStatus()))) {
            return;
        }
        article.setStatus("approved");
        articleDraftMapper.updateById(article);
    }

    private boolean hasActiveSelfMediaSchedule(Long articleId, Long excludedScheduleId) {
        if (articleId == null) {
            return false;
        }
        return scheduleMapper.countActiveByArticleId(
                articleId,
                excludedScheduleId,
                new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES)
        ) > 0;
    }

    private void refundDistributionQuotaIfPresent(SelfMediaPublishSchedule row) {
        if (row != null && row.getDistributionTaskId() != null) {
            companyChannelQuotaService.refundDistribution(row.getDistributionTaskId());
        }
    }

    private void reserveScheduleQuota(SelfMediaPublishSchedule row, Long projectId, Long companyId) {
        if (row == null || row.getId() == null || companyId == null || companyId <= 0) {
            return;
        }
        companyChannelQuotaService.reserveSelfMediaSchedule(
                companyId,
                projectId,
                row.getPlatform(),
                row.getId()
        );
    }

    private void reserveScheduleQuotas(Long companyId, List<ScheduleQuotaReservation> reservations) {
        if (companyId == null || companyId <= 0 || reservations == null || reservations.isEmpty()) {
            return;
        }
        companyChannelQuotaService.reserveSelfMediaSchedules(
                companyId,
                reservations.stream()
                        .map(item -> new CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation(
                                item.projectId(),
                                item.platform(),
                                item.scheduleId()
                        ))
                        .toList()
        );
    }

    private void confirmScheduleQuotaIfPresent(SelfMediaPublishSchedule row) {
        if (row != null && row.getId() != null) {
            companyChannelQuotaService.confirmSelfMediaSchedule(row.getId());
        }
    }

    private void refundScheduleQuotaIfPresent(SelfMediaPublishSchedule row) {
        if (row != null && row.getId() != null) {
            companyChannelQuotaService.refundSelfMediaSchedule(row.getId());
        }
    }

    private String distributionTaskPrepareFailureCode(BizException ex) {
        String message = ex == null ? "" : String.valueOf(ex.getMessage());
        if (message.contains("Distribution quota exhausted")) {
            return "DISTRIBUTION_QUOTA_EXHAUSTED";
        }
        return "DISTRIBUTION_TASK_PREPARE_FAILED";
    }

    private String resolvePublishCheckLocationName(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            return null;
        }
        if (StringUtils.hasText(brand.getSelfMediaPublishLocationName())) {
            return brand.getSelfMediaPublishLocationName().trim();
        }
        if (StringUtils.hasText(brand.getCityName())) {
            return brand.getCityName().trim();
        }
        if (StringUtils.hasText(brand.getServiceArea())) {
            return brand.getServiceArea().trim();
        }
        return null;
    }

    private String publishCheckFingerprint(SelfMediaPublishSchedule row) {
        String plannedAt = row.getPlatformScheduledAt() == null
                ? ""
                : row.getPlatformScheduledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return sha256(String.join("|",
                String.valueOf(row.getPlatform()),
                String.valueOf(row.getSelfMediaAccountId()),
                String.valueOf(row.getArticleId()),
                plannedAt,
                String.valueOf(row.getPublishCheckTitle()),
                String.valueOf(row.getPublishCheckLocationName())
        ));
    }

    private boolean isExpiredPlatformScheduleExecution(SelfMediaPublishSchedule row, LocalDateTime now) {
        if (row == null || !SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION.equals(row.getQueueKind())) {
            return false;
        }
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(row.getScheduleStrategy()))) {
            return false;
        }
        LocalDateTime platformScheduledAt = row.getPlatformScheduledAt();
        int minRemainingMinutes = scheduleAdapterRouter.rules(row.getPlatform(), row.getScheduleStrategy()).minRemainingMinutes();
        return platformScheduledAt != null
                && minRemainingMinutes > 0
                && !platformScheduledAt.isAfter(now.plusMinutes(minRemainingMinutes));
    }

    private boolean isPlatformScheduleTooClose(String strategy,
                                               LocalDateTime executionAt,
                                               LocalDateTime plannedAt,
                                               String platform) {
        int requiredLeadMinutes = requiredPlatformScheduleCreateLeadMinutes(strategy, platform);
        return SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))
                && plannedAt != null
                && executionAt != null
                && requiredLeadMinutes > 0
                && plannedAt.isBefore(executionAt.plusMinutes(requiredLeadMinutes));
    }

    private boolean isPlatformScheduleTooFar(String strategy,
                                             LocalDateTime executionAt,
                                             LocalDateTime plannedAt,
                                             String platform) {
        int maxRemainingMinutes = maxPlatformScheduleRemainingMinutes(strategy, platform);
        return SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))
                && plannedAt != null
                && executionAt != null
                && maxRemainingMinutes > 0
                && plannedAt.isAfter(executionAt.plusMinutes(maxRemainingMinutes));
    }

    private int requiredPlatformScheduleCreateLeadMinutes(String strategy, String platform) {
        SelfMediaPlatformScheduleRules rules = scheduleAdapterRouter.rules(platform, strategy);
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))) {
            return 0;
        }
        return Math.max(rules.minRemainingMinutes(), rules.fillLeadMinutes());
    }

    private int maxPlatformScheduleRemainingMinutes(String strategy, String platform) {
        if (!SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))) {
            return 0;
        }
        return scheduleAdapterRouter.rules(platform, strategy).maxRemainingMinutes();
    }

    private String minutesText(int minutes) {
        if (minutes > 0 && minutes % (24 * 60) == 0) {
            return (minutes / (24 * 60)) + " 天";
        }
        if (minutes > 0 && minutes % 60 == 0) {
            return (minutes / 60) + " 小时";
        }
        return minutes + " 分钟";
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
        payload.put("executionWindowStart", request.executionWindowStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("executionWindowEnd", request.executionWindowEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
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
        int attemptNo = Math.max(1, schedule.getAttemptCount() == null ? 1 : schedule.getAttemptCount());
        return "schedule-" + schedule.getId() + "-gen-" + schedule.getGenerationNo() + "-attempt-" + attemptNo;
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

    private QuickSchedulePlan quickResponse(String action,
                                            String code,
                                            String message,
                                            Long articleId,
                                            Long brandId,
                                            String platform,
                                            Long accountId,
                                            Long replaceScheduleId,
                                            LocalDateTime plannedPublishAt,
                                            LocalDateTime nextAttemptAt) {
        return new QuickSchedulePlan(SelfMediaPlatformQuickScheduleResponse.builder()
                .action(action)
                .code(code)
                .message(message)
                .articleId(articleId)
                .brandId(brandId)
                .platform(platform)
                .platformLabel(platformLabel(platform))
                .selfMediaAccountId(accountId)
                .replaceScheduleId(replaceScheduleId)
                .plannedPublishAt(plannedPublishAt)
                .nextAttemptAt(nextAttemptAt)
                .brandSafetyIntervalMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES)
                .build(), null);
    }

    private String firstRejectedMessage(SelfMediaPublishScheduleCreateResponse response) {
        if (response != null && response.getRejectedItems() != null && !response.getRejectedItems().isEmpty()) {
            SelfMediaPublishScheduleRejectedItemVO rejected = response.getRejectedItems().get(0);
            return StringUtils.hasText(rejected.getMessage()) ? rejected.getMessage() : "平台快速排期未创建";
        }
        return "平台快速排期未创建";
    }

    private String actionForRejected(String code) {
        String normalized = normalize(code);
        if (normalized.contains("quota")) return "quota_exhausted";
        if (normalized.contains("environment") || normalized.contains("account")) return "account_or_environment_not_ready";
        if (normalized.contains("capability") || normalized.contains("strategy")) return "rejected";
        if (normalized.contains("article")) return "article_type_mismatch";
        return "rejected";
    }

    private SelfMediaAccount selectActivePlatformAccount(Long brandId, String platform) {
        String accountPlatform = normalizePublishPlatform(platform);
        if (!StringUtils.hasText(accountPlatform)) {
            accountPlatform = normalize(platform);
        }
        return selfMediaAccountMapper.selectOne(new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getBrandId, brandId)
                .eq(SelfMediaAccount::getPlatform, accountPlatform)
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .orderByDesc(SelfMediaAccount::getUpdatedAt)
                .orderByDesc(SelfMediaAccount::getId)
                .last("LIMIT 1"));
    }

    private String quickScheduleStrategy(String platform) {
        return scheduleAdapterRouter.contract(platform)
                .map(contract -> contract.supportsBackendDelayedPublish()
                        ? SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH
                        : SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE)
                .orElse(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE);
    }

    private String articlePlatformIncompatibleMessage(ArticleDraft article, String platform) {
        String explicit = explicitArticlePlatform(article);
        if (StringUtils.hasText(explicit) && !platform.equals(explicit)) {
            return "当前文章类型适配" + platformLabel(explicit) + "，不能发布到" + platformLabel(platform) + "。请先选择适配该平台的文章后再创建排期。";
        }
        return null;
    }

    private String explicitArticlePlatform(ArticleDraft article) {
        if (article == null) return null;
        String joined = normalize(String.join(" ",
                String.valueOf(article.getTargetChannel()),
                String.valueOf(article.getContentStyle()),
                String.valueOf(article.getChannelGroupCode()),
                String.valueOf(article.getChannelSubCode()),
                String.valueOf(article.getArticleTypeCode()),
                String.valueOf(article.getTemplateSource())
        ));
        if (joined.contains("xiaohongshu") || joined.contains("xhs") || joined.contains("小红书")) return "xiaohongshu";
        if (joined.contains("toutiao") || joined.contains("头条")) return "toutiao";
        if (joined.contains("baijiahao") || joined.contains("百家号")) return "baijiahao";
        if (joined.contains("zhihu") || joined.contains("知乎")) return "zhihu";
        if (joined.contains("douyin") || joined.contains("抖音")) return "douyin";
        if (joined.contains("wechat") || joined.contains("公众号")) return "wechat";
        return null;
    }

    private LocalDateTime nextBrandSafeAttemptAt(Long brandId, LocalDateTime now) {
        return nextBrandSafeAttemptAt(brandId, now.plusSeconds(10).withNano(0), null);
    }

    private LocalDateTime nextBrandProtectedImmediateAttemptAt(Long brandId, LocalDateTime now) {
        LocalDateTime cursor = now.plusMinutes(QUICK_DISPATCH_MANUAL_START_DELAY_MINUTES).withSecond(0).withNano(0);
        if (brandId == null) {
            return cursor;
        }
        LocalDateTime protectionStart = now.minusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES);
        LocalDateTime protectionEnd = now.plusMinutes(QUICK_DISPATCH_REPLACE_PROTECTION_MINUTES);
        List<SelfMediaPublishSchedule> protectedSlots = scheduleMapper.selectBrandActiveScheduleSlots(
                brandId,
                protectionStart,
                protectionEnd.plusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES),
                new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES)
        );
        boolean moved;
        do {
            moved = false;
            for (SelfMediaPublishSchedule slot : protectedSlots == null ? List.<SelfMediaPublishSchedule>of() : protectedSlots) {
                LocalDateTime occupied = slot.getNextAttemptAt() != null ? slot.getNextAttemptAt() : slot.getPlannedPublishAt();
                if (occupied == null || occupied.isBefore(protectionStart) || occupied.isAfter(protectionEnd)) {
                    continue;
                }
                if (!cursor.isAfter(occupied.plusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES))) {
                    cursor = occupied.plusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES).plusSeconds(10).withNano(0);
                    moved = true;
                }
            }
        } while (moved);
        return cursor;
    }

    private LocalDateTime nextBrandSafeAttemptAt(Long brandId, LocalDateTime candidate, Long excludedScheduleId) {
        LocalDateTime cursor = clampToBusinessAttemptWindow(candidate);
        if (brandId == null || cursor == null) {
            return cursor;
        }
        List<SelfMediaPublishSchedule> slots = scheduleMapper.selectBrandActiveScheduleSlots(
                brandId,
                cursor.minusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES),
                cursor.plusHours(QUICK_SCHEDULE_SLOT_LOOKAHEAD_HOURS),
                new ArrayList<>(SelfMediaPublishScheduleConstants.ACTIVE_STATUSES)
        );
        boolean moved;
        do {
            moved = false;
            for (SelfMediaPublishSchedule slot : slots == null ? List.<SelfMediaPublishSchedule>of() : slots) {
                if (excludedScheduleId != null && excludedScheduleId.equals(slot.getId())) {
                    continue;
                }
                LocalDateTime occupied = slot.getNextAttemptAt() != null ? slot.getNextAttemptAt() : slot.getPlannedPublishAt();
                if (occupied == null) continue;
                long distance = Math.abs(java.time.Duration.between(occupied, cursor).toMinutes());
                if (distance < QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES) {
                    cursor = clampToBusinessAttemptWindow(
                            occupied.plusMinutes(QUICK_SCHEDULE_BRAND_INTERVAL_MINUTES).withSecond(0).withNano(0));
                    moved = true;
                }
            }
        } while (moved);
        return cursor;
    }

    private LocalDateTime plannedPublishAtForQuickSchedule(String platform,
                                                           String strategy,
                                                           LocalDateTime nextAttemptAt,
                                                           LocalDateTime now) {
        SelfMediaPlatformScheduleRules rules = scheduleAdapterRouter.rules(platform, strategy);
        int fillLead = Math.max(0, rules.fillLeadMinutes());
        LocalDateTime planned = nextAttemptAt.plusMinutes(fillLead);
        int requiredLead = requiredPlatformScheduleCreateLeadMinutes(strategy, platform);
        if (requiredLead > 0 && !planned.isAfter(now.plusMinutes(requiredLead))) {
            planned = now.plusMinutes(requiredLead + 1).withSecond(0).withNano(0);
        }
        int maxRemaining = maxPlatformScheduleRemainingMinutes(strategy, platform);
        if (maxRemaining > 0 && planned.isAfter(now.plusMinutes(maxRemaining))) {
            fail("PLATFORM_SCHEDULE_TIME_TOO_FAR", platformLabel(platform) + "定时发布时间超过平台可选范围");
        }
        return planned.withSecond(0).withNano(0);
    }

    private PeriodWindow currentMonth(LocalDateTime now) {
        LocalDateTime start = LocalDateTime.of(now.getYear(), now.getMonth(), 1, 0, 0);
        return new PeriodWindow(start, start.plusMonths(1));
    }

    private String platformLabel(String platform) {
        return switch (normalize(platform)) {
            case "toutiao" -> "头条";
            case "baijiahao" -> "百家号";
            case "zhihu" -> "知乎";
            case "xiaohongshu" -> "小红书";
            case "douyin" -> "抖音图文";
            case "wechat", "wechat_mp" -> "微信公众号";
            default -> StringUtils.hasText(platform) ? platform : "自媒体平台";
        };
    }

    private Set<String> platformsByChannel(SelfMediaPlatformPublishChannel channel) {
        Set<String> platforms = scheduleAdapterRouter.platformsByChannel(channel);
        return normalizePlatforms(platforms);
    }

    private Set<String> normalizePlatforms(Set<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String platform : platforms) {
            String value = normalizePublishPlatform(platform);
            if (!StringUtils.hasText(value)) {
                value = normalize(platform);
            }
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizePublishPlatform(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String publishPlatform = ArticlePromptChannels.canonicalSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(publishPlatform) ? publishPlatform : normalized;
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    private void enrichAlerts(List<SelfMediaPublishScheduleVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, List<com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleAlertVO>> alerts =
                alertService.listOpenAlertsByScheduleIds(records.stream().map(SelfMediaPublishScheduleVO::getId).toList());
        for (SelfMediaPublishScheduleVO record : records) {
            record.setActiveAlerts(alerts.getOrDefault(record.getId(), List.of()));
        }
    }

    private void enrichDisplayNames(List<SelfMediaPublishScheduleVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> brandIds = records.stream()
                .map(SelfMediaPublishScheduleVO::getBrandId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, String> brandNames = new LinkedHashMap<>();
        if (!brandIds.isEmpty()) {
            List<Brand> brands = brandMapper.selectBatchIds(brandIds);
            for (Brand brand : brands == null ? List.<Brand>of() : brands) {
                if (brand != null && brand.getId() != null) {
                    brandNames.put(brand.getId(), displayBrandName(brand));
                }
            }
        }

        List<Long> accountIds = records.stream()
                .map(SelfMediaPublishScheduleVO::getSelfMediaAccountId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, String> accountNames = new LinkedHashMap<>();
        if (!accountIds.isEmpty()) {
            List<SelfMediaAccount> accounts = selfMediaAccountMapper.selectBatchIds(accountIds);
            for (SelfMediaAccount account : accounts == null ? List.<SelfMediaAccount>of() : accounts) {
                if (account != null && account.getId() != null) {
                    accountNames.put(account.getId(), account.getAccountName());
                }
            }
        }

        List<Long> articleIds = records.stream()
                .map(SelfMediaPublishScheduleVO::getArticleId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, String> articleTitles = new LinkedHashMap<>();
        if (!articleIds.isEmpty()) {
            List<ArticleDraft> articles = articleDraftMapper.selectBatchIds(articleIds);
            for (ArticleDraft article : articles == null ? List.<ArticleDraft>of() : articles) {
                if (article != null && article.getId() != null) {
                    articleTitles.put(article.getId(), article.getTitle());
                }
            }
        }

        for (SelfMediaPublishScheduleVO record : records) {
            record.setArticleTitle(articleTitles.get(record.getArticleId()));
            record.setBrandName(brandNames.get(record.getBrandId()));
            record.setSelfMediaAccountName(accountNames.get(record.getSelfMediaAccountId()));
        }
    }

    private String displayBrandName(Brand brand) {
        if (brand == null) {
            return null;
        }
        if (StringUtils.hasText(brand.getBrandShortName())) {
            return brand.getBrandShortName();
        }
        return brand.getBrandName();
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

    private QuotaPrecheck quotaPrecheck(Long brandId) {
        Brand brand = brandId == null ? null : brandMapper.selectById(brandId);
        return new QuotaPrecheck(brand == null ? null : brand.getCompanyId());
    }

    private class QuotaPrecheck {
        private final Long companyId;
        private final Map<String, Integer> remainingByPlatform = new LinkedHashMap<>();
        private final Map<String, String> unavailableMessageByPlatform = new LinkedHashMap<>();

        private QuotaPrecheck(Long companyId) {
            this.companyId = companyId;
        }

        private SelfMediaPublishScheduleRejectedItemVO check(Long articleId, Long accountId, String platform) {
            String normalizedPlatform = normalize(platform);
            if (!StringUtils.hasText(normalizedPlatform)) {
                return rejected(articleId, accountId, platform,
                        "CHANNEL_QUOTA_UNAVAILABLE",
                        "无法识别自媒体平台，不能校验渠道额度",
                        "客户套餐 > 渠道额度");
            }
            if (companyId == null || companyId <= 0) {
                return rejected(articleId, accountId, platform,
                        "CHANNEL_QUOTA_UNAVAILABLE",
                        "品牌未关联有效客户，不能校验渠道额度",
                        "客户套餐 > 渠道额度");
            }
            if (unavailableMessageByPlatform.containsKey(normalizedPlatform)) {
                return rejected(articleId, accountId, platform,
                        "CHANNEL_QUOTA_UNAVAILABLE",
                        unavailableMessageByPlatform.get(normalizedPlatform),
                        "客户套餐 > 渠道额度");
            }
            Integer remaining = remainingByPlatform.computeIfAbsent(normalizedPlatform, this::loadRemaining);
            if (remaining == null) {
                return rejected(articleId, accountId, platform,
                        "CHANNEL_QUOTA_UNAVAILABLE",
                        unavailableMessageByPlatform.getOrDefault(normalizedPlatform, "自媒体渠道额度配置不可用"),
                        "客户套餐 > 渠道额度");
            }
            if (remaining <= 0) {
                return rejected(articleId, accountId, platform,
                        "CHANNEL_QUOTA_EXHAUSTED",
                        "该自媒体平台渠道额度已用完，请调整套餐额度或释放未使用任务后再创建排期",
                        "客户套餐 > 渠道额度");
            }
            return null;
        }

        private void consume(String platform) {
            String normalizedPlatform = normalize(platform);
            Integer remaining = remainingByPlatform.get(normalizedPlatform);
            if (remaining != null) {
                remainingByPlatform.put(normalizedPlatform, remaining - 1);
            }
        }

        private Integer loadRemaining(String platform) {
            try {
                CompanyChannelQuotaService.DistributionQuotaView quota =
                        companyChannelQuotaService.selfMediaDistributionQuota(companyId, platform);
                return Math.max(0, quota.quotaLimit() - quota.usedCount());
            } catch (BizException ex) {
                unavailableMessageByPlatform.put(platform, ex.getMessage());
                return null;
            }
        }
    }

    private record ValidatedRequest(Long brandId,
                                    List<Long> articleIds,
                                    List<Long> accountIds,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    LocalDateTime executionWindowStart,
                                    LocalDateTime executionWindowEnd,
                                    boolean hasExplicitExecutionWindow,
                                    String strategy,
                                    int intervalMinutes) {
    }

    private record PeriodWindow(LocalDateTime start, LocalDateTime end) {
    }

    private record QuickScheduleData(Candidate candidate,
                                     String strategy,
                                     LocalDateTime plannedPublishAt,
                                     LocalDateTime nextAttemptAt) {
    }

    private record QuickSchedulePlan(SelfMediaPlatformQuickScheduleResponse response,
                                     QuickScheduleData data) {
        QuickSchedulePlan withPlan(QuickScheduleData data) {
            return new QuickSchedulePlan(response, data);
        }
    }

    private record Candidate(ArticleDraft article,
                             SelfMediaAccount account,
                             BrowserEnvironmentAccount binding,
                             SelfMediaPublishScheduleRejectedItemVO rejected) {
        static Candidate rejected(SelfMediaPublishScheduleRejectedItemVO item) {
            return new Candidate(null, null, null, item);
        }
    }

    private record ScheduleQuotaReservation(Long scheduleId,
                                            Long projectId,
                                            String platform) {
    }

    private record QueueClaimProfile(List<String> expectedStatuses, String targetStatus) {
    }

    public record ClaimedScheduleTask(SelfMediaPublishScheduleVO schedule, DistributionTask task) {
    }
}
