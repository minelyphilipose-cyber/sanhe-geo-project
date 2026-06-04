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
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ArticleRevisionSaveRequest;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.content.service.render.wechat.WechatArticleRenderService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentArticleService {

    private static final Set<String> AUTO_APPROVED_GENERATED_BY = Set.of("ai", "system", "batch_ai", "ai_preview", "template_ai");
    private static final Set<String> LEGACY_PROJECT_UPDATE_ROLES =
            Set.of("operator", "delivery_manager", "partner", "partner_staff");

    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final ArticleReviewLogMapper articleReviewLogMapper;
    private final ArticlePublishLogMapper articlePublishLogMapper;
    private final BatchArticleGenerationTaskMapper batchArticleGenerationTaskMapper;
    private final ArticlePromptTemplateMapper articlePromptTemplateMapper;
    private final BrandMapper brandMapper;
    private final ProjectMapper projectMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final CurrentUserService currentUserService;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final WechatArticleRenderService wechatArticleRenderService;
    private final ArticleImagePublicUrlRewriter articleImagePublicUrlRewriter;
    private final ArticleAutoImageInsertionService autoImageInsertionService;
    private final ArticleCoverSelectionService coverSelectionService;
    private final BrandAccessService brandAccessService;
    private final AuditService auditService;

    public Page<ArticleDraft> page(String projectName, String status, String articleType, long current, long size) {
        return page(projectName, status, articleType, null, null, null, null, null, null, current, size);
    }

    public Page<ArticleDraft> page(String projectName,
                                   String status,
                                   String articleType,
                                   String articleTypeCode,
                                   String channelGroupCode,
                                   String channelSubCode,
                                   String generationMode,
                                   String createdStartDate,
                                   String createdEndDate,
                                   long current,
                                   long size) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        LambdaQueryWrapper<ArticleDraft> wrapper = new LambdaQueryWrapper<ArticleDraft>()
                .ne(ArticleDraft::getStatus, "deleted")
                .orderByDesc(ArticleDraft::getCreatedAt);
        if (StringUtils.hasText(projectName) || currentUserService.isPartnerUser(operator)) {
            List<Long> projectIds = resolveReadableProjectIds(operator, projectName);
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
        applyArticleTypeCodeFilter(wrapper, articleTypeCode);
        applyChannelFilter(wrapper, channelGroupCode, channelSubCode);
        applyGenerationModeFilter(wrapper, generationMode);
        applyCreatedDateFilter(wrapper, createdStartDate, createdEndDate);
        Page<ArticleDraft> pageData = articleDraftMapper.selectPage(new Page<>(current, size), wrapper);
        fillProjectNames(pageData.getRecords());
        fillGenerationMetadata(pageData.getRecords());
        return pageData;
    }

    private void applyArticleTypeCodeFilter(LambdaQueryWrapper<ArticleDraft> wrapper, String articleTypeCode) {
        String code = trimToNull(articleTypeCode);
        if (code == null) {
            return;
        }
        wrapper.eq(ArticleDraft::getArticleTypeCode, code);
    }

    private void applyChannelFilter(LambdaQueryWrapper<ArticleDraft> wrapper, String channelGroupCode, String channelSubCode) {
        String group = trimToNull(channelGroupCode);
        String sub = trimToNull(channelSubCode);
        if (group == null) {
            return;
        }
        if (sub != null) {
            wrapper.eq(ArticleDraft::getChannelGroupCode, group)
                    .eq(ArticleDraft::getChannelSubCode, sub);
            return;
        }
        wrapper.eq(ArticleDraft::getChannelGroupCode, group);
    }

    private void applyGenerationModeFilter(LambdaQueryWrapper<ArticleDraft> wrapper, String generationMode) {
        String mode = trimToNull(generationMode);
        if (mode == null) {
            return;
        }
        String batchGeneratedSql = """
                SELECT article_id
                FROM batch_article_generation_task
                WHERE article_id IS NOT NULL
                UNION
                SELECT article_id
                FROM article_draft_version
                WHERE generated_by = 'batch_ai'
                """;
        if ("batch".equals(mode)) {
            wrapper.inSql(ArticleDraft::getId, batchGeneratedSql);
        } else if ("single".equals(mode)) {
            wrapper.notInSql(ArticleDraft::getId, batchGeneratedSql);
        }
    }

    private void applyCreatedDateFilter(LambdaQueryWrapper<ArticleDraft> wrapper, String createdStartDate, String createdEndDate) {
        LocalDate startDate = parseDate(createdStartDate);
        LocalDate endDate = parseDate(createdEndDate);
        if (startDate != null) {
            wrapper.ge(ArticleDraft::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(ArticleDraft::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
    }

    private LocalDate parseDate(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return LocalDate.parse(text);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String groupFromContentStyle(String contentStyle) {
        String style = trimToNull(contentStyle);
        if (style == null) {
            return null;
        }
        if (ArticlePromptChannels.SELF_MEDIA_SUBS.contains(style)) {
            return ArticlePromptChannels.SELF_MEDIA;
        }
        if ("agent_site_article".equals(style) || "linkedin".equals(style)) {
            return ArticlePromptChannels.AGENT_SITE;
        }
        if (ArticlePromptChannels.INDUSTRY_SITE.equals(style)) {
            return ArticlePromptChannels.INDUSTRY_SITE;
        }
        if (ArticlePromptChannels.AUTHORITY_MEDIA.equals(style)) {
            return ArticlePromptChannels.AUTHORITY_MEDIA;
        }
        if (ArticlePromptChannels.FORUM.equals(style)) {
            return ArticlePromptChannels.FORUM;
        }
        return ArticlePromptChannels.isValidCode(style) ? style : null;
    }

    private String subFromContentStyle(String contentStyle) {
        String style = trimToNull(contentStyle);
        if (style == null) {
            return null;
        }
        if (ArticlePromptChannels.SELF_MEDIA_SUBS.contains(style)) {
            return style;
        }
        if (ArticlePromptChannels.AUTHORITY_MEDIA.equals(style)) {
            return "industry_media";
        }
        return null;
    }

    public String publicPreviewHtml(Long articleId) {
        ArticleDraft article = requireArticle(articleId);
        if (!Set.of("approved", "distributing", "distributed", "published", "unpublished").contains(article.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Article is not available for public preview");
        }
        ArticleDraftVersion version = articleDraftVersionMapper.selectOne(
                new LambdaQueryWrapper<ArticleDraftVersion>()
                        .eq(ArticleDraftVersion::getArticleId, articleId)
                        .orderByDesc(ArticleDraftVersion::getVersionNo)
                        .last("limit 1")
        );
        if (version == null) {
            throw new BizException(ContentErrorCodes.ARTICLE_NOT_FOUND, "Article version not found");
        }
        String title = StringUtils.hasText(version.getTitle()) ? version.getTitle() : article.getTitle();
        String content = Optional.ofNullable(version.getContentMarkdown()).orElse("");
        Project project = requireProject(article.getProjectId());
        String html = wechatArticleRenderService.renderOrFallback(article, articleImagePublicUrlRewriter.rewrite(project, content));
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body{margin:0;background:#f8fafc;color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.75}
                    main{max-width:820px;margin:0 auto;padding:40px 20px 64px;background:#fff;min-height:100vh}
                    h1{font-size:28px;line-height:1.35;margin:0 0 24px}
                    img{max-width:100%%;height:auto;border-radius:6px}
                    table{border-collapse:collapse;width:100%%}
                    th,td{border:1px solid #e2e8f0;padding:8px;text-align:left}
                  </style>
                </head>
                <body><main>%s</main></body>
                </html>
                """.formatted(escapeHtml(title), html);
    }

    public Map<String, Object> detail(Long articleId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, false);
        fillGenerationMetadata(List.of(article));
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
        BatchArticleGenerationTask batchGenerationTask = batchArticleGenerationTaskMapper.selectOne(
                new LambdaQueryWrapper<BatchArticleGenerationTask>()
                        .eq(BatchArticleGenerationTask::getArticleId, articleId)
                        .orderByDesc(BatchArticleGenerationTask::getId)
                        .last("limit 1")
        );
        fillPromptTemplateName(article);
        fillPromptTemplateName(batchGenerationTask);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("article", article);
        result.put("project", project);
        result.put("batchGenerationTask", batchGenerationTask);
        result.put("versions", versions);
        result.put("reviewLogs", reviewLogs);
        result.put("publishLogs", publishLogs);
        return result;
    }

    @Transactional
    public ArticleDraft createManual(ManualArticleCreateRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.article.write", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
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
        String contentStyle = StringUtils.hasText(req.getContentStyle()) ? req.getContentStyle().trim() : "";
        String channelGroupCode = groupFromContentStyle(contentStyle);
        String channelSubCode = subFromContentStyle(contentStyle);
        String topic = StringUtils.hasText(req.getTopic()) ? req.getTopic().trim() : "";
        String topicAsQuestion = StringUtils.hasText(req.getTopicAsQuestion()) ? req.getTopicAsQuestion().trim() : null;
        if (!StringUtils.hasText(contentStyle)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Content style is required");
        }
        if (!StringUtils.hasText(topic)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Topic is required");
        }
        String createSource = normalizeCreateSource(req.getSource());
        String coverImageUrl = resolveManualCreateCoverUrl(project, req, channelGroupCode, channelSubCode, contentStyle, createSource);
        markdownImageReferenceValidator.validate(project, content);

        String initialStatus = "approved";

        ArticleDraft draft = new ArticleDraft();
        draft.setProjectId(project.getId());
        draft.setArticleType(articleType);
        draft.setArticleTypeCode(articleType);
        draft.setContentStyle(contentStyle);
        draft.setChannelGroupCode(channelGroupCode);
        draft.setChannelSubCode(channelSubCode);
        draft.setTopic(topic);
        draft.setTopicAsQuestion(topicAsQuestion);
        draft.setTitle(title);
        draft.setCoverImageUrl(coverImageUrl);
        draft.setStatus(initialStatus);
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
        version.setGeneratedBy(createSource);
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
        auditArticleTransition("ARTICLE_CREATED", AuditResult.SUCCESS, operator, project, draft, null, initialStatus, "manual create", null);
        return draft;
    }

    private String resolveManualCreateCoverUrl(Project project,
                                               ManualArticleCreateRequest req,
                                               String channelGroupCode,
                                               String channelSubCode,
                                               String contentStyle,
                                               String createSource) {
        if (!isSelfMediaChannel(channelGroupCode, channelSubCode, contentStyle)) {
            return null;
        }
        if ("ai_preview".equals(createSource) && req.getCoverMaterialId() == null) {
            return coverSelectionService.selectRandomCoverUrl(project.getBrandId());
        }
        return coverSelectionService.requireManualCoverUrl(project.getBrandId(), req.getCoverMaterialId());
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
        currentUserService.ensurePermissionOrLegacy("content.article.write", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
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
        String newStatus = "approved";
        // Entity is null intentionally; all updated columns are set explicitly in the wrapper,
        // keeping the status predicate and mutation in one atomic UPDATE.
        int updated = articleDraftMapper.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, oldStatus)
                .set(ArticleDraft::getTitle, title)
                .set(ArticleDraft::getCurrentVersionNo, nextVersion)
                .set(ArticleDraft::getStatus, newStatus)
                .set(ArticleDraft::getHasRisk, riskResult.hasRisk)
                .set(ArticleDraft::getRiskSeverity, riskResult.severity)
                .set(ArticleDraft::getRiskWordsJson, riskResult.wordsJson)
                .set(ArticleDraft::getIsDuplicateTitle, duplicateResult.duplicate)
                .set(ArticleDraft::getDuplicateScore, duplicateResult.score)
                .set(ArticleDraft::getDuplicateArticleId, duplicateResult.articleId));
        if (updated != 1) {
            auditArticleTransition("ARTICLE_REVISION_SAVED", AuditResult.DENIED, operator, project, article, oldStatus, newStatus, "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_REVISION_SAVED", AuditResult.SUCCESS, operator, project, article, oldStatus, newStatus, req.getNote(), null);

    }

    @Transactional
    public void resubmit(Long articleId, ArticleResubmitRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.article.write", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Article review workflow is disabled");
    }

    @Transactional
    public void review(Long articleId, ArticleReviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.article.write", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.MANAGE);
        throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Article review workflow is disabled");
    }

    @Transactional
    public void publish(Long articleId, ArticlePublishRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.publish.operate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
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
    public void deleteUnpublished(Long articleId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.article.write", "project.update", LEGACY_PROJECT_UPDATE_ROLES);
        ArticleDraft article = requireArticle(articleId);
        Project project = requireProject(article.getProjectId());
        ensureProjectAccess(operator, project, true);
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);

        String oldStatus = article.getStatus();
        if (Set.of("published", "distributed", "distributing").contains(oldStatus)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Published or distributing article cannot be deleted");
        }

        int updated = articleDraftMapper.update(null, new LambdaUpdateWrapper<ArticleDraft>()
                .eq(ArticleDraft::getId, articleId)
                .eq(ArticleDraft::getStatus, oldStatus)
                .set(ArticleDraft::getStatus, "deleted"));
        if (updated != 1) {
            auditArticleTransition("ARTICLE_DELETED", AuditResult.DENIED, operator, project, article, oldStatus, "deleted", "STALE_STATE", ContentErrorCodes.ARTICLE_STATE_CONFLICT);
            throw new BizException(ContentErrorCodes.ARTICLE_STATE_CONFLICT, "Article state conflict");
        }
        auditArticleTransition("ARTICLE_DELETED", AuditResult.SUCCESS, operator, project, article, oldStatus, "deleted", "delete unpublished article", null);
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
        contentMarkdown = autoImageInsertionService.insertForChannel(project, targetChannel, contentMarkdown);
        markdownImageReferenceValidator.validate(project, contentMarkdown);
        ArticleDraft draft = new ArticleDraft();
        draft.setBatchId(batchId);
        draft.setProjectId(project.getId());
        draft.setTargetChannel(targetChannel);
        draft.setPeriodType(periodType);
        draft.setPeriodKey(periodKey);
        draft.setGenerationSlotNo(generationSlotNo);
        draft.setArticleType(articleType);
        draft.setArticleTypeCode(articleType);
        draft.setTitle(title);
        if (isSelfMediaTargetChannel(targetChannel)) {
            draft.setCoverImageUrl(coverSelectionService.selectRandomCoverUrl(project.getBrandId()));
        }
        draft.setStatus("approved");
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
        if (article == null || "deleted".equals(article.getStatus())) {
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

    private boolean isSelfMediaChannel(String channelGroupCode, String channelSubCode, String contentStyle) {
        if (ArticlePromptChannels.SELF_MEDIA.equals(channelGroupCode)) {
            return true;
        }
        if (StringUtils.hasText(channelSubCode) && ArticlePromptChannels.SELF_MEDIA_SUBS.contains(channelSubCode.trim())) {
            return true;
        }
        return StringUtils.hasText(contentStyle) && ArticlePromptChannels.SELF_MEDIA_SUBS.contains(contentStyle.trim());
    }

    private boolean isSelfMediaTargetChannel(String targetChannel) {
        if (!StringUtils.hasText(targetChannel)) {
            return false;
        }
        String channel = targetChannel.trim();
        return ArticlePromptChannels.SELF_MEDIA.equals(channel) || channel.startsWith(ArticlePromptChannels.SELF_MEDIA + ":");
    }

    private void fillGenerationMetadata(List<ArticleDraft> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        List<Long> articleIds = articles.stream()
                .map(ArticleDraft::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            return;
        }
        Map<Long, BatchArticleGenerationTask> taskMap = batchArticleGenerationTaskMapper.selectList(
                        new LambdaQueryWrapper<BatchArticleGenerationTask>()
                                .in(BatchArticleGenerationTask::getArticleId, articleIds)
                                .orderByDesc(BatchArticleGenerationTask::getId)
                ).stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getArticleId() != null)
                .collect(Collectors.toMap(BatchArticleGenerationTask::getArticleId, task -> task, (first, ignored) -> first));
        for (ArticleDraft article : articles) {
            article.setSystemGenerated(false);
            article.setGenerationMode("single");
            BatchArticleGenerationTask task = taskMap.get(article.getId());
            if (task != null) {
                article.setSystemGenerated(true);
                article.setGenerationMode("batch");
                fillArticleFromGenerationTask(article, task);
            }
        }
        Map<Long, String> generatedByMap = articleDraftVersionMapper.selectList(
                        new LambdaQueryWrapper<ArticleDraftVersion>()
                                .in(ArticleDraftVersion::getArticleId, articleIds)
                                .select(ArticleDraftVersion::getArticleId, ArticleDraftVersion::getGeneratedBy)
                                .orderByDesc(ArticleDraftVersion::getVersionNo)
                ).stream()
                .filter(Objects::nonNull)
                .filter(version -> version.getArticleId() != null)
                .filter(version -> StringUtils.hasText(version.getGeneratedBy()))
                .collect(Collectors.toMap(
                        ArticleDraftVersion::getArticleId,
                        version -> version.getGeneratedBy().trim().toLowerCase(Locale.ROOT),
                        (first, ignored) -> first
                ));
        for (ArticleDraft article : articles) {
            String generatedBy = generatedByMap.get(article.getId());
            article.setGeneratedBy(generatedBy);
            if (isAutoApprovedGeneratedBy(generatedBy)) {
                article.setSystemGenerated(true);
            }
        }
        fillPromptTemplateNames(articles);
    }

    private void fillArticleFromGenerationTask(ArticleDraft article, BatchArticleGenerationTask task) {
        if (!StringUtils.hasText(article.getTopic())) {
            article.setTopic(task.getTopic());
        }
        if (!StringUtils.hasText(article.getTopicAsQuestion())) {
            article.setTopicAsQuestion(task.getTopicAsQuestion());
        }
    }

    private void fillPromptTemplateNames(List<ArticleDraft> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        List<Long> templateIds = articles.stream()
                .map(ArticleDraft::getPromptTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (templateIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = articlePromptTemplateMapper.selectList(
                        new LambdaQueryWrapper<ArticlePromptTemplate>()
                                .in(ArticlePromptTemplate::getId, templateIds)
                                .select(ArticlePromptTemplate::getId, ArticlePromptTemplate::getName)
                ).stream()
                .collect(Collectors.toMap(ArticlePromptTemplate::getId, ArticlePromptTemplate::getName, (a, b) -> a));
        for (ArticleDraft article : articles) {
            article.setPromptTemplateName(nameMap.get(article.getPromptTemplateId()));
        }
    }

    private void fillPromptTemplateName(ArticleDraft article) {
        if (article == null || article.getPromptTemplateId() == null || StringUtils.hasText(article.getPromptTemplateName())) {
            return;
        }
        ArticlePromptTemplate template = articlePromptTemplateMapper.selectById(article.getPromptTemplateId());
        if (template != null) {
            article.setPromptTemplateName(template.getName());
        }
    }

    private void fillPromptTemplateName(BatchArticleGenerationTask task) {
        if (task == null || task.getPromptTemplateId() == null) {
            return;
        }
        ArticlePromptTemplate template = articlePromptTemplateMapper.selectById(task.getPromptTemplateId());
        if (template != null) {
            task.setPromptTemplateName(template.getName());
        }
    }

    private boolean isAutoApprovedGeneratedBy(String generatedBy) {
        return StringUtils.hasText(generatedBy)
                && AUTO_APPROVED_GENERATED_BY.contains(generatedBy.trim().toLowerCase(Locale.ROOT));
    }

    private List<Long> resolveReadableProjectIds(SysUser operator, String projectName) {
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<Project>()
                .isNull(Project::getDeletedAt)
                .select(Project::getId);
        if (StringUtils.hasText(projectName)) {
            projectWrapper.like(Project::getProjectName, projectName.trim());
        }
        if (currentUserService.isPartnerUser(operator)) {
            projectWrapper.eq(Project::getPartnerId, operator.getPartnerId());
        }
        return projectMapper.selectList(projectWrapper).stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void ensureProjectAccess(SysUser operator, Project project, boolean write) {
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
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

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record RiskResult(boolean hasRisk, String severity, String wordsJson) {}
    private record DuplicateResult(boolean duplicate, BigDecimal score, Long articleId) {
        static DuplicateResult none() {
            return new DuplicateResult(false, BigDecimal.ZERO, null);
        }
    }
}
