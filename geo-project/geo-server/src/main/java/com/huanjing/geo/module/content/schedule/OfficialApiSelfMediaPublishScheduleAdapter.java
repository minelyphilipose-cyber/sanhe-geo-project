package com.huanjing.geo.module.content.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticleDraftVersion;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.service.ArticleImagePublicUrlRewriter;
import com.huanjing.geo.module.content.service.SelfMediaPublishMaterialSelectionService;
import com.huanjing.geo.module.content.service.SelfMediaScheduleCapabilityService;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OfficialApiSelfMediaPublishScheduleAdapter implements SelfMediaPublishScheduleAdapter {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SelfMediaPlatformScheduleAdapterRouter platformRouter;
    private final List<AutoSelfMediaAdapter> adapters;
    private final SelfMediaPublishScheduleMapper scheduleMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final ProjectMapper projectMapper;
    private final ArticleImagePublicUrlRewriter articleImagePublicUrlRewriter;
    private final SelfMediaPublishMaterialSelectionService materialSelectionService;
    private final SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String platform) {
        String normalized = normalize(platform);
        return StringUtils.hasText(normalized)
                && platformRouter.contract(normalized)
                .map(contract -> SelfMediaPlatformPublishChannel.OFFICIAL_API.equals(contract.publishChannel()))
                .orElse(false)
                && adapterMap().containsKey(normalized);
    }

    @Override
    public ScheduleExecutionResult schedule(SelfMediaPublishScheduleVO schedule) {
        SelfMediaPublishSchedule row = requireSchedule(schedule);
        DistributionTask existing = existingTask(row);
        if (existing != null && !"failed".equalsIgnoreCase(String.valueOf(existing.getStatus()))) {
            linkTask(row, existing);
            return ScheduleExecutionResult.scheduled(
                    firstText(existing.getPlatformPublishId(), existing.getPlatformArticleId()),
                    diagnostics("official_api_reused", row, existing, null, null)
            );
        }

        SelfMediaAccount account = requireAccount(row.getSelfMediaAccountId(), row.getPlatform());
        AutoSelfMediaAdapter adapter = requireAdapter(row.getPlatform());
        ScheduleExecutionResult preflight = preflightCredential(row, account, adapter);
        if (preflight != null) {
            return preflight;
        }

        ArticleDraft article = requireArticle(row.getArticleId());
        Project project = requireProject(article.getProjectId());
        String originalContent = requireLatestContent(article.getId());
        TargetContext.SelfMediaTarget target = buildTarget(row, article, project, account, originalContent);
        String content = articleImagePublicUrlRewriter.rewriteForBrand(materialBrandId(project, article), originalContent);

        DistributionTask task = createTask(row, article, account);
        SubmitResult result;
        try {
            result = adapter.submitToTarget(article, content, target);
        } catch (Exception ex) {
            result = SubmitResult.failure(500, null, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }
        finalizeTask(task, result);
        DistributionTask latest = distributionTaskMapper.selectById(task.getId());
        linkTask(row, latest == null ? task : latest);

        if (!result.isSuccess()) {
            return result.isRetryable()
                    ? ScheduleExecutionResult.retryable(
                    firstText(result.getFailureKind(), "OFFICIAL_API_SUBMIT_RETRY"),
                    firstText(result.getErrorMessage(), "官方 API 提交失败，等待重试"),
                    diagnostics("official_api_submit_failed", row, latest, result, null),
                    LocalDateTime.now().plusMinutes(5)
            )
                    : ScheduleExecutionResult.failed(
                    firstText(result.getFailureKind(), "OFFICIAL_API_SUBMIT_FAILED"),
                    firstText(result.getErrorMessage(), "官方 API 提交失败"),
                    diagnostics("official_api_submit_failed", row, latest, result, null)
            );
        }
        return ScheduleExecutionResult.scheduled(
                firstText(result.getPlatformPublishId(), result.getPlatformArticleId()),
                diagnostics("official_api_submitted", row, latest, result, null)
        );
    }

    @Override
    public PublishCheckResult checkPublishResult(SelfMediaPublishScheduleVO schedule) {
        SelfMediaPublishSchedule row = requireSchedule(schedule);
        DistributionTask task = existingTask(row);
        if (task == null) {
            return PublishCheckResult.unknown(diagnostics("official_api_task_missing", row, null, null, null));
        }
        SelfMediaAccount account = requireAccount(task.getSelfMediaAccountId(), row.getPlatform());
        AutoSelfMediaAdapter adapter = requireAdapter(row.getPlatform());
        ReviewStatusResult status;
        try {
            status = adapter.refreshReviewStatus(task, account);
        } catch (Exception ex) {
            status = ReviewStatusResult.unknown(null, safeMessage(ex), true, null);
        }
        if (status == null || status.status() == null) {
            status = ReviewStatusResult.unknown(null, null, true, null);
        }
        updateReviewStatus(task, status);
        DistributionTask latest = distributionTaskMapper.selectById(task.getId());
        String diagnostics = diagnostics("official_api_review_check", row, latest, null, status);

        return switch (status.status()) {
            case PUBLISHED -> PublishCheckResult.published(latest == null ? null : latest.getPublishedUrl(), diagnostics);
            case REJECTED, OFFLINE -> PublishCheckResult.failed(
                    status.status() == ReviewStatusResult.ReviewStatus.REJECTED
                            ? "OFFICIAL_API_REVIEW_REJECTED"
                            : "OFFICIAL_API_WORK_OFFLINE",
                    firstText(status.reviewFeedback(), "官方 API 回查确认发布未通过或已下线"),
                    diagnostics
            );
            case UNDER_REVIEW, UNKNOWN -> status.retryable()
                    ? PublishCheckResult.retryable(
                    "OFFICIAL_API_REVIEW_PENDING",
                    firstText(status.reviewFeedback(), "官方 API 发布结果仍在审核或暂未返回最终状态"),
                    diagnostics,
                    LocalDateTime.now().plusMinutes(10)
            )
                    : PublishCheckResult.unknown(diagnostics);
            case NOT_APPLICABLE -> PublishCheckResult.unknown(diagnostics);
        };
    }

    private Map<String, AutoSelfMediaAdapter> adapterMap() {
        return adapters.stream().collect(Collectors.toMap(
                adapter -> normalize(adapter.platform()),
                Function.identity(),
                (left, ignored) -> left
        ));
    }

    private AutoSelfMediaAdapter requireAdapter(String platform) {
        AutoSelfMediaAdapter adapter = adapterMap().get(normalize(platform));
        if (adapter == null) {
            throw new BizException(400, "官方 API 自媒体适配器未接入：" + platform);
        }
        return adapter;
    }

    private ScheduleExecutionResult preflightCredential(SelfMediaPublishSchedule row,
                                                        SelfMediaAccount account,
                                                        AutoSelfMediaAdapter adapter) {
        try {
            adapter.preflightCredential(account);
            return null;
        } catch (BizException ex) {
            String diagnostics = diagnostics("official_api_credential_preflight_failed", row, null,
                    SubmitResult.failure(ex.getCode(), null, null, ex.getMessage(), preflightFailureKind(ex), preflightRetryable(ex)),
                    null);
            if (preflightRetryable(ex)) {
                return ScheduleExecutionResult.retryable(
                        preflightFailureCode(ex),
                        firstText(ex.getMessage(), "官方 API 凭证预检暂时失败，等待重试"),
                        diagnostics,
                        LocalDateTime.now().plusMinutes(5)
                );
            }
            return ScheduleExecutionResult.failed(
                    preflightFailureCode(ex),
                    firstText(ex.getMessage(), "官方 API 凭证预检失败，请重新授权"),
                    diagnostics
            );
        }
    }

    private boolean preflightRetryable(BizException ex) {
        int code = ex == null ? 0 : ex.getCode();
        return code == 429 || code >= 500;
    }

    private String preflightFailureCode(BizException ex) {
        int code = ex == null ? 0 : ex.getCode();
        if (code == 429) {
            return "OFFICIAL_API_CREDENTIAL_REFRESHING";
        }
        if (code == 401 || code == 40001 || code == 42001 || code == 61023) {
            return "OFFICIAL_API_CREDENTIAL_EXPIRED";
        }
        if (code == 403 || code == 48001) {
            return "OFFICIAL_API_PERMISSION_MISSING";
        }
        if (code >= 500) {
            return "OFFICIAL_API_CREDENTIAL_PRECHECK_RETRY";
        }
        return "OFFICIAL_API_CREDENTIAL_PRECHECK_FAILED";
    }

    private String preflightFailureKind(BizException ex) {
        int code = ex == null ? 0 : ex.getCode();
        if (code == 401 || code == 40001 || code == 42001 || code == 61023) {
            return FailureKind.AUTH_EXPIRED;
        }
        if (code == 403 || code == 48001) {
            return FailureKind.PERMISSION;
        }
        if (code == 429) {
            return FailureKind.RATE_LIMIT;
        }
        if (code >= 500) {
            return FailureKind.SERVER_ERROR;
        }
        return FailureKind.AUTH;
    }

    private TargetContext.SelfMediaTarget buildTarget(SelfMediaPublishSchedule row,
                                                       ArticleDraft article,
                                                       Project project,
                                                       SelfMediaAccount account,
                                                       String originalContent) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("scheduleId", row.getId());
        options.put("scheduleStrategy", row.getScheduleStrategy());
        options.put("publishAction", "publish");
        Long materialBrandId = materialBrandId(project, article);
        if (materialBrandId != null) {
            options.put("materialBrandId", materialBrandId);
        }
        if (row.getPlannedPublishAt() != null) {
            options.put("plannedPublishAt", row.getPlannedPublishAt().format(ISO));
        }
        if (row.getPlatformScheduledAt() != null) {
            options.put("platformScheduledAt", row.getPlatformScheduledAt().format(ISO));
        }
        Map<String, Object> automationOptions = scheduleCapabilityService.automationOptions(row.getPlatform());
        if (automationOptions != null) {
            options.putAll(automationOptions);
        }
        SelfMediaPublishMaterialSelectionService.Selection selection =
                materialSelectionService.select(project, article, originalContent);
        Long coverMaterialId = longOption(options, "coverMaterialId");
        if (coverMaterialId == null && selection.coverMaterialId() != null) {
            coverMaterialId = selection.coverMaterialId();
            options.put("coverMaterialId", coverMaterialId);
            options.put("coverMaterialAutoSelected", true);
        }
        List<Long> imageMaterialIds = longListOption(options, "imageMaterialIds");
        if (imageMaterialIds.isEmpty() && !selection.imageMaterialIds().isEmpty()) {
            imageMaterialIds = selection.imageMaterialIds();
            options.put("imageMaterialIds", imageMaterialIds);
            options.put("imageMaterialAutoSelected", true);
        }
        return new TargetContext.SelfMediaTarget(
                account,
                coverMaterialId,
                imageMaterialIds,
                stringListOption(options, "hashtags"),
                intOption(options, "privateStatus"),
                intOption(options, "downloadType"),
                requestId(row),
                options
        );
    }

    private Long materialBrandId(Project project, ArticleDraft article) {
        if (article != null && article.getSubjectBrandId() != null) {
            return article.getSubjectBrandId();
        }
        return project == null ? null : project.getBrandId();
    }

    private DistributionTask createTask(SelfMediaPublishSchedule row, ArticleDraft article, SelfMediaAccount account) {
        DistributionTask task = new DistributionTask();
        task.setArticleId(article.getId());
        task.setProjectId(article.getProjectId());
        task.setTargetKind(DistributionTargetKind.MP_ACCOUNT);
        task.setSelfMediaAccountId(account.getId());
        task.setAttemptNo(nextAttemptNo(article.getId(), account.getId()));
        task.setStatus("submitting");
        task.setIntegrationMethod(row.getPlatform());
        task.setDispatchMode("AUTO");
        task.setRetryCount(0);
        task.setOperatorId(row.getUpdatedBy() == null ? row.getCreatedBy() : row.getUpdatedBy());
        task.setRequestId(requestId(row));
        task.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        distributionTaskMapper.insert(task);
        return task;
    }

    private void finalizeTask(DistributionTask task, SubmitResult result) {
        LocalDateTime now = LocalDateTime.now();
        task.setLockedUntil(null);
        task.setFinishedAt(now);
        task.setRequestPayload(jsonColumn(result.getRequestPayload()));
        task.setResponsePayload(jsonColumn(result.getResponseBody()));
        task.setExternalStatus(result.getExternalStatus());
        task.setReviewStatus(result.getReviewStatus());
        task.setReviewFeedback(result.getReviewFeedback());
        if (result.isSuccess()) {
            task.setStatus("submitted");
            task.setPlatformArticleId(result.getPlatformArticleId());
            task.setPlatformPublishId(result.getPlatformPublishId());
            task.setPublishedUrl(result.getPublishedUrl());
            task.setSubmittedAt(now);
            task.setReviewCheckCount(0);
            task.setFailureKind(null);
            task.setErrorMessage(null);
            task.setNextRetryAt(null);
        } else {
            task.setStatus("failed");
            task.setFailureKind(firstText(result.getFailureKind(), FailureKind.UNKNOWN));
            task.setErrorMessage(firstText(result.getErrorMessage(), "official api submit failed"));
            task.setNextRetryAt(result.isRetryable() ? now.plusMinutes(5) : null);
        }
        distributionTaskMapper.updateById(task);
    }

    private void updateReviewStatus(DistributionTask task, ReviewStatusResult status) {
        if (task == null || status == null) {
            return;
        }
        DistributionTask next = distributionTaskMapper.selectById(task.getId());
        if (next == null) {
            return;
        }
        next.setReviewStatus(SubmitResult.toStorageValue(status.status()));
        next.setExternalStatus(status.externalStatus());
        next.setReviewFeedback(status.reviewFeedback());
        next.setResponsePayload(jsonColumn(status.rawResponse()));
        next.setReviewCheckedAt(LocalDateTime.now());
        next.setReviewCheckCount((next.getReviewCheckCount() == null ? 0 : next.getReviewCheckCount()) + 1);
        next.setReviewLockedUntil(null);
        if (StringUtils.hasText(status.platformArticleId())) {
            next.setPlatformArticleId(status.platformArticleId());
        }
        if (StringUtils.hasText(status.publishedUrl())) {
            next.setPublishedUrl(status.publishedUrl());
        }
        if (ReviewStatusResult.ReviewStatus.PUBLISHED.equals(status.status())) {
            next.setStatus("published");
            next.setPublishedAt(LocalDateTime.now());
            next.setFinishedAt(LocalDateTime.now());
        } else if (ReviewStatusResult.ReviewStatus.REJECTED.equals(status.status())) {
            next.setStatus("failed");
            next.setFailureKind(FailureKind.PLATFORM);
            next.setErrorMessage(firstText(status.reviewFeedback(), "official api review rejected"));
            next.setFinishedAt(LocalDateTime.now());
        } else if (ReviewStatusResult.ReviewStatus.OFFLINE.equals(status.status())) {
            next.setStatus("published");
            next.setFailureKind(FailureKind.PLATFORM);
            next.setErrorMessage(firstText(status.reviewFeedback(), "official api work offline"));
            next.setFinishedAt(LocalDateTime.now());
        } else if (status.retryable()) {
            next.setNextReviewCheckAt(LocalDateTime.now().plusMinutes(10));
        }
        distributionTaskMapper.updateById(next);
    }

    private DistributionTask existingTask(SelfMediaPublishSchedule row) {
        if (row == null) {
            return null;
        }
        if (row.getDistributionTaskId() != null) {
            DistributionTask task = distributionTaskMapper.selectById(row.getDistributionTaskId());
            if (task != null) {
                return task;
            }
        }
        return distributionTaskMapper.selectOne(new LambdaQueryWrapper<DistributionTask>()
                .eq(DistributionTask::getRequestId, requestId(row))
                .last("LIMIT 1"));
    }

    private void linkTask(SelfMediaPublishSchedule row, DistributionTask task) {
        if (row == null || task == null || task.getId() == null || task.getId().equals(row.getDistributionTaskId())) {
            return;
        }
        row.setDistributionTaskId(task.getId());
        row.setUpdatedAt(LocalDateTime.now());
        scheduleMapper.updateById(row);
    }

    private int nextAttemptNo(Long articleId, Long accountId) {
        return distributionTaskMapper.selectList(new LambdaQueryWrapper<DistributionTask>()
                        .eq(DistributionTask::getArticleId, articleId)
                        .eq(DistributionTask::getSelfMediaAccountId, accountId)
                        .select(DistributionTask::getAttemptNo))
                .stream()
                .map(DistributionTask::getAttemptNo)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private SelfMediaPublishSchedule requireSchedule(SelfMediaPublishScheduleVO vo) {
        Long id = vo == null ? null : vo.getId();
        if (id == null || id <= 0) {
            throw new BizException(400, "schedule id is required");
        }
        SelfMediaPublishSchedule row = scheduleMapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "自媒体排期不存在");
        }
        return row;
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(404, "文章不存在");
        }
        return article;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "项目不存在");
        }
        return project;
    }

    private SelfMediaAccount requireAccount(Long accountId, String platform) {
        SelfMediaAccount account = accountId == null ? null : selfMediaAccountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(404, "自媒体账号不存在");
        }
        if (StringUtils.hasText(platform) && !normalize(platform).equals(normalize(account.getPlatform()))) {
            throw new BizException(400, "自媒体账号平台与排期平台不一致");
        }
        return account;
    }

    private String requireLatestContent(Long articleId) {
        ArticleDraftVersion latest = articleDraftVersionMapper.selectOne(new LambdaQueryWrapper<ArticleDraftVersion>()
                .eq(ArticleDraftVersion::getArticleId, articleId)
                .orderByDesc(ArticleDraftVersion::getVersionNo)
                .last("LIMIT 1"));
        if (latest == null || !StringUtils.hasText(latest.getContentMarkdown())) {
            throw new BizException(400, "Article content is empty");
        }
        return latest.getContentMarkdown();
    }

    private String diagnostics(String event,
                               SelfMediaPublishSchedule row,
                               DistributionTask task,
                               SubmitResult submitResult,
                               ReviewStatusResult reviewStatus) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("adapter", "official_api_self_media");
        root.put("event", event);
        if (row != null) {
            root.put("scheduleId", row.getId());
            root.put("platform", row.getPlatform());
            if (row.getPlatformScheduledAt() != null) {
                root.put("platformScheduledAt", row.getPlatformScheduledAt().format(ISO));
            }
        }
        if (task != null) {
            root.put("distributionTaskId", task.getId());
            root.put("taskStatus", task.getStatus());
            root.put("reviewStatus", task.getReviewStatus());
            root.put("externalStatus", task.getExternalStatus());
            root.put("platformArticleId", firstText(task.getPlatformArticleId(), ""));
            root.put("platformPublishId", firstText(task.getPlatformPublishId(), ""));
        }
        if (submitResult != null) {
            root.put("submitSuccess", submitResult.isSuccess());
            root.put("operationStage", firstText(submitResult.getOperationStage(), ""));
            root.put("operationStageLabel", operationStageLabel(submitResult.getOperationStage()));
            root.put("failureKind", firstText(submitResult.getFailureKind(), ""));
            root.put("errorMessage", firstText(submitResult.getErrorMessage(), ""));
            if (StringUtils.hasText(submitResult.getResponseBody())) {
                root.put("platformRawError", submitResult.getResponseBody());
            }
        }
        if (reviewStatus != null) {
            root.put("reviewOutcome", reviewStatus.status().name().toLowerCase(Locale.ROOT));
            root.put("reviewRetryable", reviewStatus.retryable());
            root.put("reviewFeedback", firstText(reviewStatus.reviewFeedback(), ""));
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ignored) {
            return "{\"adapter\":\"official_api_self_media\"}";
        }
    }

    private String operationStageLabel(String operationStage) {
        if (!StringUtils.hasText(operationStage)) {
            return "";
        }
        return switch (operationStage) {
            case "WECHAT_PREPARE_COVER_MATERIAL" -> "准备公众号封面素材";
            case "WECHAT_RENDER_CONTENT" -> "转换公众号正文与图片";
            case "WECHAT_ADD_DRAFT" -> "新增公众号草稿";
            case "WECHAT_SUBMIT_PUBLISH" -> "提交公众号发布";
            default -> operationStage;
        };
    }

    private String requestId(SelfMediaPublishSchedule row) {
        return "self-media-schedule:" + row.getId();
    }

    private String jsonColumn(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (Exception ignored) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("value", value);
            try {
                return objectMapper.writeValueAsString(root);
            } catch (JsonProcessingException ex) {
                return "{\"value\":\"unserializable\"}";
            }
        }
    }

    private Long longOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer intOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Long> longListOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.longValue());
            } else if (item instanceof String text && StringUtils.hasText(text)) {
                try {
                    result.add(Long.parseLong(text.trim()));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid optional IDs.
                }
            }
        }
        return result;
    }

    private List<String> stringListOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String publishPlatform = ArticlePromptChannels.normalizeSelfMediaPublishPlatform(normalized);
        return StringUtils.hasText(publishPlatform) ? publishPlatform : normalized;
    }

    private String safeMessage(Exception ex) {
        String message = ex == null ? null : ex.getMessage();
        return StringUtils.hasText(message) ? message.trim() : ex == null ? "unknown" : ex.getClass().getSimpleName();
    }
}
