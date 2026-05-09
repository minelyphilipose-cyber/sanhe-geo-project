package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ArticleRevisionSaveRequest;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentArticleService {

    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleReviewLogMapper articleReviewLogMapper;
    private final ArticlePublishLogMapper articlePublishLogMapper;
    private final BrandMapper brandMapper;
    private final ProjectMapper projectMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final CurrentUserService currentUserService;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final BrandAccessService brandAccessService;
    private final AuditService auditService;

    public Page<ArticleDraft> page(Long projectId, String status, String articleType, long current, long size) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<ArticleDraft> wrapper = new LambdaQueryWrapper<ArticleDraft>()
                .orderByDesc(ArticleDraft::getCreatedAt);
        if (projectId != null) {
            Project project = requireProject(projectId);
            ensureProjectAccess(operator, project, false);
            wrapper.eq(ArticleDraft::getProjectId, projectId);
        } else if (currentUserService.isPartnerUser(operator)) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .isNull(Project::getDeletedAt)
                            .eq(Project::getPartnerId, operator.getPartnerId())
                            .select(Project::getId)
            ).stream().map(Project::getId).collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                return new Page<>(current, size);
            }
            wrapper.in(ArticleDraft::getProjectId, projectIds);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ArticleDraft::getStatus, status.trim());
        }
        if (StringUtils.hasText(articleType)) {
            wrapper.eq(ArticleDraft::getArticleType, articleType.trim());
        }
        Page<ArticleDraft> pageData = articleDraftMapper.selectPage(new Page<>(current, size), wrapper);
        fillProjectNames(pageData.getRecords());
        return pageData;
    }

    public Map<String, Object> detail(Long articleId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, false);
        List<ArticleDraftVersion> versions = articleDraftVersionMapper.selectList(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getArticleId, articleId)
                        .orderByDesc(ArticleDraftVersion::getVersionNo)
        );
        List<ArticleReviewLog> reviewLogs = articleReviewLogMapper.selectList(
                new LambdaQueryWrapper<ArticleReviewLog>()
                        .eq(ArticleReviewLog::getArticleId, articleId)
                        .orderByDesc(ArticleReviewLog::getCreatedAt)
        );
        List<ArticlePublishLog> publishLogs = articlePublishLogMapper.selectList(
                new LambdaQueryWrapper<ArticlePublishLog>()
                        .eq(ArticlePublishLog::getArticleId, articleId)
                        .orderByDesc(ArticlePublishLog::getCreatedAt)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("article", article);
        result.put("project", project);
        result.put("versions", versions);
        result.put("reviewLogs", reviewLogs);
        result.put("publishLogs", publishLogs);
        return result;
    }

    @Transactional
    public ArticleDraft createManual(ManualArticleCreateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        Project project = requireProject(req.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);

        String articleType = StringUtils.hasText(req.getArticleType()) ? req.getArticleType().trim() : "";
        if (!ArticleTypes.isSupported(articleType)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid article type");
        }
        String content = req.getContentMarkdown().trim();
        String title = StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : "";
        if (!StringUtils.hasText(title)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Title is required");
        }
        markdownImageReferenceValidator.validate(project, content);

        ArticleDraft draft = new ArticleDraft();
        draft.setProjectId(project.getId());
        draft.setArticleType(articleType);
        draft.setTitle(title);
        draft.setStatus("pending_review");
        draft.setCurrentVersionNo(1);
        draft.setHasRisk(false);
        draft.setRiskSeverity("none");
        draft.setIsDuplicateTitle(false);
        articleDraftMapper.insert(draft);

        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(draft.getId());
        version.setVersionNo(1);
        version.setTitle(title);
        version.setContentMarkdown(content);
        version.setPromptSnapshot(aiPromptSnapshot(req.getAiMetadata()));
        version.setInputSnapshot(aiInputSnapshot(req.getAiMetadata()));
        version.setModelPlatformCode(aiMetadataString(req.getAiMetadata(), "modelPlatformCode"));
        version.setModelId(aiMetadataString(req.getAiMetadata(), "modelId"));
        version.setGeneratedBy(normalizeCreateSource(req.getSource()));
        version.setCreatedBy(operator.getId());
        articleDraftVersionMapper.insert(version);

        RiskResult riskResult = scanRisk(project, content);
        DuplicateResult duplicateResult = checkDuplicate(draft, title);
        draft.setHasRisk(riskResult.hasRisk);
        draft.setRiskSeverity(riskResult.severity);
        draft.setRiskWordsJson(riskResult.wordsJson);
        draft.setIsDuplicateTitle(duplicateResult.duplicate);
        draft.setDuplicateScore(duplicateResult.score);
        draft.setDuplicateArticleId(duplicateResult.articleId);
        articleDraftMapper.updateById(draft);
        draft.setProjectName(project.getProjectName());
        auditArticleTransition("ARTICLE_CREATED", AuditResult.SUCCESS, operator, project, draft, null, "pending_review", "manual create", null);
        return draft;
    }

    private String normalizeCreateSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "manual";
        }
        String value = source.trim();
        if ("ai_preview".equals(value)) {
            return value;
        }
        return "manual";
    }

    private String aiPromptSnapshot(Map<String, Object> aiMetadata) {
        if (aiMetadata == null || aiMetadata.isEmpty()) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contentSource", "AI_PREVIEW");
        snapshot.put("promptSnapshot", aiMetadata.get("promptSnapshot"));
        snapshot.put("modelResponseSnapshot", aiMetadata.get("modelResponseSnapshot"));
        snapshot.put("modelPlatformCode", aiMetadata.get("modelPlatformCode"));
        snapshot.put("modelId", aiMetadata.get("modelId"));
        snapshot.put("modelName", aiMetadata.get("modelName"));
        return JSONUtil.toJsonStr(snapshot);
    }

    private String aiInputSnapshot(Map<String, Object> aiMetadata) {
        if (aiMetadata == null || aiMetadata.isEmpty()) {
            return null;
        }
        Object inputSnapshot = aiMetadata.get("inputSnapshot");
        if (inputSnapshot == null) {
            return null;
        }
        return inputSnapshot instanceof String value ? value : JSONUtil.toJsonStr(inputSnapshot);
    }

    private String aiMetadataString(Map<String, Object> aiMetadata, String key) {
        if (aiMetadata == null) {
            return null;
        }
        Object value = aiMetadata.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    @Transactional
    public void saveRevision(Long articleId, ArticleRevisionSaveRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);

        String oldStatus = article.getStatus();
        String content = req.getContentMarkdown().trim();
        String title = StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : extractTitle(content);
        int nextVersion = Optional.ofNullable(article.getCurrentVersionNo()).orElse(1) + 1;
        markdownImageReferenceValidator.validate(project, content);

        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(articleId);
        version.setVersionNo(nextVersion);
        version.setTitle(title);
        version.setContentMarkdown(content);
        version.setGeneratedBy("manual");
        version.setCreatedBy(operator.getId());
        articleDraftVersionMapper.insert(version);

        RiskResult riskResult = scanRisk(project, content);
        DuplicateResult duplicateResult = checkDuplicate(article, title);
        // Entity is null intentionally; all updated columns are set explicitly in the wrapper,
        // keeping the status predicate and mutation in one atomic UPDATE.
        int updated = articleDraftMapper.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, oldStatus)
                .set(ArticleDraft::getTitle, title)
                .set(ArticleDraft::getCurrentVersionNo, nextVersion)
                .set(ArticleDraft::getStatus, "under_revision")
                .set(ArticleDraft::getHasRisk, riskResult.hasRisk)
                .set(ArticleDraft::getRiskSeverity, riskResult.severity)
                .set(ArticleDraft::getRiskWordsJson, riskResult.wordsJson)
                .set(ArticleDraft::getIsDuplicateTitle, duplicateResult.duplicate)
                .set(ArticleDraft::getDuplicateScore, duplicateResult.score)
                .set(ArticleDraft::getDuplicateArticleId, duplicateResult.articleId));
        if (updated != 1) {
            auditArticleTransition("ARTICLE_REVISION_SAVED", AuditResult.DENIED, operator, project, article, oldStatus, "under_revision", "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_REVISION_SAVED", AuditResult.SUCCESS, operator, project, article, oldStatus, "under_revision", req.getNote(), null);

        if (StringUtils.hasText(req.getNote())) {
            ArticleReviewLog log = new ArticleReviewLog();
            log.setArticleId(articleId);
            log.setAction("return_for_revision");
            log.setComment(req.getNote().trim());
            log.setRiskOverridden(false);
            log.setOperatorId(operator.getId());
            articleReviewLogMapper.insert(log);
        }
    }

    @Transactional
    public void resubmit(Long articleId, ArticleResubmitRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        if (!Set.of("under_revision", "rejected").contains(article.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Only under_revision/rejected article can resubmit");
        }
        String oldStatus = article.getStatus();
        // Entity is null intentionally; all updated columns are set explicitly in the wrapper,
        // keeping the status predicate and mutation in one atomic UPDATE.
        int updated = articleDraftMapper.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, oldStatus)
                .set(ArticleDraft::getStatus, "pending_review"));
        if (updated != 1) {
            auditArticleTransition("ARTICLE_RESUBMITTED", AuditResult.DENIED, operator, project, article, oldStatus, "pending_review", "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_RESUBMITTED", AuditResult.SUCCESS, operator, project, article, oldStatus, "pending_review", req == null ? null : req.getComment(), null);

        ArticleReviewLog log = new ArticleReviewLog();
        log.setArticleId(articleId);
        log.setAction("resubmit");
        log.setComment(req == null ? null : req.getComment());
        log.setRiskOverridden(false);
        log.setOperatorId(operator.getId());
        articleReviewLogMapper.insert(log);
    }

    @Transactional
    public void review(Long articleId, ArticleReviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        ensureReviewerIsNotAuthor(article, operator);

        String action = req.getAction().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("approve", "reject", "return_for_revision").contains(action)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid review action");
        }
        boolean needComment = "reject".equals(action) || "return_for_revision".equals(action);
        boolean riskOverridden = false;
        if (needComment && !StringUtils.hasText(req.getComment())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Comment is required");
        }
        if ("approve".equals(action) && Boolean.TRUE.equals(article.getHasRisk())) {
            String severity = Optional.ofNullable(article.getRiskSeverity()).orElse("none");
            if ("block".equalsIgnoreCase(severity)) {
                throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Please resolve block risk words before approve");
            }
            if ("warn".equalsIgnoreCase(severity)) {
                if (!StringUtils.hasText(req.getComment())) {
                    throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Warn risk approve requires comment");
                }
                riskOverridden = true;
            }
        }

        String oldStatus = article.getStatus();
        String newStatus;
        if ("approve".equals(action)) {
            newStatus = "approved";
        } else if ("reject".equals(action)) {
            newStatus = "rejected";
        } else {
            newStatus = "under_revision";
        }
        // Entity is null intentionally; all updated columns are set explicitly in the wrapper,
        // keeping the status predicate and mutation in one atomic UPDATE.
        int updated = articleDraftMapper.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, "pending_review")
                .set(ArticleDraft::getStatus, newStatus));
        if (updated != 1) {
            auditArticleTransition("ARTICLE_REVIEWED", AuditResult.DENIED, operator, project, article, oldStatus, newStatus, "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_REVIEWED", AuditResult.SUCCESS, operator, project, article, oldStatus, newStatus, req.getComment(), null);

        ArticleReviewLog log = new ArticleReviewLog();
        log.setArticleId(articleId);
        log.setAction(action);
        log.setComment(req.getComment());
        log.setRiskOverridden(riskOverridden);
        log.setOperatorId(operator.getId());
        articleReviewLogMapper.insert(log);
    }

    @Transactional
    public void publish(Long articleId, ArticlePublishRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        String action = req.getPublishAction().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("publish", "unpublish").contains(action)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid publish action");
        }
        if ("publish".equals(action) && !Set.of("approved", "unpublished").contains(article.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Only approved/unpublished article can publish");
        }
        if ("unpublish".equals(action) && !Set.of("published", "distributed").contains(article.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Only published/distributed article can unpublish");
        }
        String oldStatus = article.getStatus();
        String newStatus = "publish".equals(action) ? "published" : "unpublished";
        LambdaUpdateWrapper<ArticleDraft> update = new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, oldStatus)
                .set(ArticleDraft::getStatus, newStatus);
        if ("publish".equals(action)) {
            update.set(ArticleDraft::getPublishedAt, LocalDateTime.now());
        }
        // Entity is null intentionally; all updated columns are set explicitly in the wrapper,
        // keeping the status predicate and mutation in one atomic UPDATE.
        int updated = articleDraftMapper.update(null, update);
        if (updated != 1) {
            auditArticleTransition("ARTICLE_PUBLISH_STATE_CHANGED", AuditResult.DENIED, operator, project, article, oldStatus, newStatus, "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_PUBLISH_STATE_CHANGED", AuditResult.SUCCESS, operator, project, article, oldStatus, newStatus, req.getNote(), null);

        ArticlePublishLog log = new ArticlePublishLog();
        log.setArticleId(articleId);
        log.setPublishAction(action);
        log.setChannelName(req.getChannelName());
        log.setChannelUrl(req.getChannelUrl());
        log.setOperatorId(operator.getId());
        log.setNote(req.getNote());
        articlePublishLogMapper.insert(log);
    }

    @Transactional
    public ArticleDraft createGeneratedDraft(Long batchId,
                                             Project project,
                                             String articleType,
                                             String title,
                                             String contentMarkdown,
                                             String promptSnapshot,
                                             String inputSnapshot,
                                             String platformCode,
                                             String modelId,
                                             String targetChannel,
                                             String periodType,
                                             String periodKey,
                                             Integer generationSlotNo) {
        markdownImageReferenceValidator.validate(project, contentMarkdown);
        ArticleDraft draft = new ArticleDraft();
        draft.setBatchId(batchId);
        draft.setProjectId(project.getId());
        draft.setTargetChannel(targetChannel);
        draft.setPeriodType(periodType);
        draft.setPeriodKey(periodKey);
        draft.setGenerationSlotNo(generationSlotNo);
        draft.setArticleType(articleType);
        draft.setTitle(title);
        draft.setStatus("pending_review");
        draft.setCurrentVersionNo(1);
        draft.setHasRisk(false);
        draft.setRiskSeverity("none");
        draft.setIsDuplicateTitle(false);
        articleDraftMapper.insert(draft);

        ArticleDraftVersion version = new ArticleDraftVersion();
        version.setArticleId(draft.getId());
        version.setVersionNo(1);
        version.setTitle(title);
        version.setContentMarkdown(contentMarkdown);
        version.setPromptSnapshot(promptSnapshot);
        version.setInputSnapshot(inputSnapshot);
        version.setModelPlatformCode(platformCode);
        version.setModelId(modelId);
        version.setGeneratedBy("system");
        articleDraftVersionMapper.insert(version);

        RiskResult riskResult = scanRisk(project, contentMarkdown);
        DuplicateResult duplicateResult = checkDuplicate(draft, title);
        draft.setHasRisk(riskResult.hasRisk);
        draft.setRiskSeverity(riskResult.severity);
        draft.setRiskWordsJson(riskResult.wordsJson);
        draft.setIsDuplicateTitle(duplicateResult.duplicate);
        draft.setDuplicateScore(duplicateResult.score);
        draft.setDuplicateArticleId(duplicateResult.articleId);
        articleDraftMapper.updateById(draft);
        return draft;
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(ContentErrorCodes.ARTICLE_NOT_FOUND, "Article not found");
        }
        return article;
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private void fillProjectNames(List<ArticleDraft> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        List<Long> projectIds = articles.stream()
                .map(ArticleDraft::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (projectIds.isEmpty()) {
            return;
        }
        Map<Long, String> projectNameMap = projectMapper.selectList(
                        new LambdaQueryWrapper<Project>()
                                .isNull(Project::getDeletedAt)
                                .in(Project::getId, projectIds)
                                .select(Project::getId, Project::getProjectName)
                ).stream()
                .collect(Collectors.toMap(Project::getId, Project::getProjectName, (a, b) -> a));
        for (ArticleDraft article : articles) {
            article.setProjectName(projectNameMap.getOrDefault(article.getProjectId(), "-"));
        }
    }

    private void ensureProjectAccess(SysUser operator, Project project, boolean write) {
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        if (write) {
            currentUserService.ensurePermission("project.update");
        }
    }

    private void ensureReviewerIsNotAuthor(ArticleDraft article, SysUser operator) {
        Long reviewerId = operator == null ? null : operator.getId();
        if (reviewerId == null) {
            return;
        }
        List<ArticleDraftVersion> versions = articleDraftVersionMapper.selectList(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getArticleId, article.getId())
                        .select(ArticleDraftVersion::getCreatedBy)
        );
        boolean authoredByReviewer = versions != null && versions.stream()
                .filter(Objects::nonNull)
                .map(ArticleDraftVersion::getCreatedBy)
                .anyMatch(reviewerId::equals);
        if (authoredByReviewer) {
            throw new BizException(ContentErrorCodes.ARTICLE_AUTHOR_CANNOT_REVIEW, "Article author cannot review their own article");
        }
    }

    private void auditArticleTransition(
            String eventType,
            AuditResult result,
            SysUser operator,
            Project project,
            ArticleDraft article,
            String oldStatus,
            String newStatus,
            String reason,
            Integer errorCode
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setMode(AuditMode.SYNC);
        event.setSensitive(false);
        event.setResult(result);
        event.setActorId(operator == null ? null : operator.getId());
        event.setBrandId(project == null ? null : project.getBrandId());
        event.setTargetType("ARTICLE");
        event.setTargetId(article == null || article.getId() == null ? null : String.valueOf(article.getId()));
        Map<String, Object> detail = new LinkedHashMap<>();
        if (article != null) {
            detail.put("articleId", article.getId());
            detail.put("projectId", article.getProjectId());
        }
        detail.put("oldStatus", oldStatus);
        detail.put("newStatus", newStatus);
        if (StringUtils.hasText(reason)) {
            detail.put("reason", reason);
        }
        event.setDetail(detail);
        if (errorCode != null) {
            event.setErrorCode(String.valueOf(errorCode));
            event.setErrorMessage(reason);
        }
        auditService.record(event);
    }

    private String extractTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return "未命名草稿";
        }
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "");
            }
            if (StringUtils.hasText(trimmed)) {
                return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
            }
        }
        return "未命名草稿";
    }

    private RiskResult scanRisk(Project project, String content) {
        List<Map<String, String>> hits = new ArrayList<>();
        boolean hasBlock = false;
        String lower = Optional.ofNullable(content).orElse("").toLowerCase(Locale.ROOT);

        Brand brand = project.getBrandId() == null ? null : brandMapper.selectById(project.getBrandId());
        if (brand != null && brand.getDeletedAt() != null) {
            brand = null;
        }
        for (String phrase : parseStringList(brand == null ? null : brand.getForbiddenPhrases())) {
            if (lower.contains(phrase.toLowerCase(Locale.ROOT))) {
                hits.add(Map.of("word", phrase, "severity", "block", "source", "brand"));
                hasBlock = true;
            }
        }
        for (String phrase : parseStringList(project.getExtraForbiddenPhrases())) {
            if (lower.contains(phrase.toLowerCase(Locale.ROOT))) {
                hits.add(Map.of("word", phrase, "severity", "block", "source", "project"));
                hasBlock = true;
            }
        }
        List<SysDictItem> globals = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, "global_forbidden_phrase")
                        .eq(SysDictItem::getEnabled, true)
        );
        boolean hasWarn = false;
        for (SysDictItem item : globals) {
            String word = item.getDictKey();
            if (!StringUtils.hasText(word)) {
                continue;
            }
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                String severity = "warn".equalsIgnoreCase(item.getDictValue()) ? "warn" : "block";
                hits.add(Map.of("word", word, "severity", severity, "source", "global"));
                if ("block".equals(severity)) {
                    hasBlock = true;
                } else {
                    hasWarn = true;
                }
            }
        }
        if (hits.isEmpty()) {
            return new RiskResult(false, "none", null);
        }
        return new RiskResult(true, hasBlock ? "block" : (hasWarn ? "warn" : "none"), JSONUtil.toJsonStr(hits));
    }

    private List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            JSONUtil.parseArray(raw).forEach(item -> {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            });
            return result.stream().distinct().collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private DuplicateResult checkDuplicate(ArticleDraft article, String newTitle) {
        if (!StringUtils.hasText(newTitle)) {
            return DuplicateResult.none();
        }
        List<ArticleDraft> candidates = articleDraftMapper.selectList(
                new LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getProjectId, article.getProjectId())
                        .eq(ArticleDraft::getArticleType, article.getArticleType())
                        .ge(ArticleDraft::getCreatedAt, LocalDateTime.now().minusDays(30))
                        .ne(article.getId() != null, ArticleDraft::getId, article.getId())
                        .orderByDesc(ArticleDraft::getCreatedAt)
        );
        BigDecimal best = BigDecimal.ZERO;
        Long hitId = null;
        for (ArticleDraft candidate : candidates) {
            BigDecimal sim = similarity(newTitle, candidate.getTitle());
            if (sim.compareTo(best) > 0) {
                best = sim;
                hitId = candidate.getId();
            }
        }
        if (best.compareTo(new BigDecimal("0.8000")) >= 0) {
            return new DuplicateResult(true, best, hitId);
        }
        return DuplicateResult.none();
    }

    private BigDecimal similarity(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return BigDecimal.ZERO;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return BigDecimal.ONE;
        }
        int dist = levenshtein(a, b);
        BigDecimal score = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(dist).divide(BigDecimal.valueOf(maxLen), 4, RoundingMode.HALF_UP)
        );
        return score.max(BigDecimal.ZERO);
    }

    private int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[n][m];
    }

    private record RiskResult(boolean hasRisk, String severity, String wordsJson) {}
    private record DuplicateResult(boolean duplicate, BigDecimal score, Long articleId) {
        static DuplicateResult none() {
            return new DuplicateResult(false, BigDecimal.ZERO, null);
        }
    }
}
