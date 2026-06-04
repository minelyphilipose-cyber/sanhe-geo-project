package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.*;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.audit.*;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.*;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.*;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ArticleAiDraftService {
    private static final Set<String> LEGACY_PROJECT_UPDATE_ROLES =
            Set.of("operator", "delivery_manager", "partner", "partner_staff");


    private static final String STATUS_APPROVED = "approved";
    private static final String GENERATED_BY_AI = "ai";
    private static final String GENERATED_BY_TEMPLATE_AI = "template_ai";
    private static final String ALLOCATION_MODE_CUSTOM = "custom";
    private static final String TEMPLATE_SOURCE_CUSTOM = "custom";
    private static final String DEFAULT_CONTENT_STYLE = "wechat";
    private static final String DEFAULT_TONE = "professional";
    private static final String DEFAULT_LENGTH = "medium";
    private static final String ARTICLE_PREVIEW_SYSTEM_PROMPT = """
            你是一名中文 GEO（生成式引擎优化）内容写作助手，负责为品牌项目生成可被大模型引用的高质量 Markdown 文章草稿。
            内容立场是行业观察者，而非品牌方市场人员。只输出完整 Markdown 正文，不输出提示词解释。
            """;
    private static final List<String> CONTACT_INTENT_KEYWORDS = List.of(
            "联系", "咨询", "电话", "地址", "怎么找", "在哪里", "到店", "预约", "客服", "门店", "网点"
    );

    private static final Map<String, String> TONE_LABELS = Map.of(
            "professional", "专业严谨",
            "friendly", "亲切自然",
            "sharp", "观点鲜明",
            "storytelling", "故事化"
    );

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final BatchArticlePromptBuilder promptBuilder;
    private final ArticleGenerationPromptContextFactory promptContextFactory;
    private final ArticleGenerationEngine articleGenerationEngine;
    private final ArticleCoverSelectionService coverSelectionService;
    private final ArticleAiDraftRateLimiter rateLimiter;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Executor articleAiDraftExecutor;

    public ArticleAiDraftService(ProjectMapper projectMapper, BrandMapper brandMapper,
                                 ArticleDraftMapper articleDraftMapper, ArticleDraftVersionMapper articleDraftVersionMapper,
                                 CurrentUserService currentUserService,
                                 BrandAccessService brandAccessService,
                                 BatchArticlePromptBuilder promptBuilder,
                                 ArticleGenerationPromptContextFactory promptContextFactory,
                                 ArticleGenerationEngine articleGenerationEngine,
                                 ArticleCoverSelectionService coverSelectionService,
                                 ArticleAiDraftRateLimiter rateLimiter,
                                 AuditService auditService, ObjectMapper objectMapper,
                                 PlatformTransactionManager transactionManager,
                                 @Qualifier("articleAiDraftExecutor") Executor articleAiDraftExecutor) {
        this.projectMapper = projectMapper; this.brandMapper = brandMapper;
        this.articleDraftMapper = articleDraftMapper; this.articleDraftVersionMapper = articleDraftVersionMapper;
        this.currentUserService = currentUserService;
        this.brandAccessService = brandAccessService;
        this.promptBuilder = promptBuilder;
        this.promptContextFactory = promptContextFactory;
        this.articleGenerationEngine = articleGenerationEngine;
        this.coverSelectionService = coverSelectionService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService; this.objectMapper = objectMapper;
        this.transactionManager = transactionManager; this.articleAiDraftExecutor = articleAiDraftExecutor;
    }

    public CompletableFuture<ArticleAiDraftResponse> generate(ArticleAiDraftRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);

        Project project = requireProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        rateLimiter.check(operator.getId());

        String articleType = normalizeArticleType(req.getArticleType());
        String originalPrompt = req.getPrompt().trim();
        Brand brand = resolveBrand(project.getBrandId());

        return CompletableFuture.supplyAsync(
                () -> generateInWorker(project, brand, operator, articleType, originalPrompt,
                        req.getModelPlatformCode(), req.getModelId()),
                articleAiDraftExecutor
        );
    }

    public CompletableFuture<ArticleAiDraftPreviewResponse> preview(ArticleAiDraftPreviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);

        Project project = requireProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        rateLimiter.check(operator.getId());

        String articleType = normalizeArticleType(req.getArticleType());
        Brand brand = resolveBrand(project.getBrandId());
        BatchArticlePromptBuilder.PromptBuildResult prompt = buildPreviewPrompt(req, articleType, project, brand);
        boolean allowContactInfo = shouldIncludeContactInfo(articleType, req.getTopic());
        String inputSnapshot = prompt.inputSnapshot();

        return CompletableFuture.supplyAsync(
                () -> previewInWorker(project, operator, prompt, inputSnapshot,
                        req.getModelPlatformCode(), req.getModelId(), allowContactInfo),
                articleAiDraftExecutor
        );
    }

    public CompletableFuture<ArticleTemplatePreviewResponse> templatePreview(ArticleTemplatePreviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);

        ArticleGenerationPromptContextFactory.PromptContextResult context = prepareTemplateContext(req, operator);

        return CompletableFuture.supplyAsync(
                () -> templatePreviewInWorker(context, operator, req.getModelPlatformCode(), req.getModelId()),
                articleAiDraftExecutor
        );
    }

    public CompletableFuture<ArticleAiDraftResponse> templateGenerate(ArticleTemplatePreviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy("content.ai.generate", "project.update", LEGACY_PROJECT_UPDATE_ROLES);

        ArticleGenerationPromptContextFactory.PromptContextResult context = prepareTemplateContext(req, operator);

        return CompletableFuture.supplyAsync(
                () -> templateGenerateInWorker(context, operator, req.getModelPlatformCode(), req.getModelId()),
                articleAiDraftExecutor
        );
    }

    private ArticleGenerationPromptContextFactory.PromptContextResult prepareTemplateContext(ArticleTemplatePreviewRequest req,
                                                                                            SysUser operator) {
        Project project = requireProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        rateLimiter.check(operator.getId());

        String topicSource = req.getKeywordGroupId() == null ? "manual" : "keyword_group";
        PromptContextRequest contextRequest = new PromptContextRequest(
                req.getProjectId(),
                topicSource,
                req.getArticleType(),
                req.getChannelGroupCode(),
                req.getChannelSubCode(),
                req.getTopic(),
                req.getTopicAsQuestion(),
                defaultOption(req.getLength(), DEFAULT_LENGTH),
                req.getKeywordGroupId(),
                null,
                req.getExtraPrompt(),
                req.getPromptTemplateId(),
                req.getPromptTemplateVersionId(),
                null,
                null,
                null,
                1
        );
        return promptContextFactory.buildStrict(contextRequest);
    }

    private ArticleTemplatePreviewResponse templatePreviewInWorker(ArticleGenerationPromptContextFactory.PromptContextResult context,
                                                                   SysUser operator,
                                                                   String requestedPlatformCode,
                                                                   String requestedModelId) {
        long started = System.nanoTime();
        ArticleModelResolver.ModelSelection model = null;
        try {
            ArticleGenerationEngine.GeneratedArticle generated = articleGenerationEngine.generate(
                    new ArticleGenerationEngine.GenerateInput(
                            context.project(),
                            context.brand(),
                            context.prompt().systemPrompt(),
                            context.prompt().userPrompt(),
                            requestedPlatformCode,
                            requestedModelId,
                            true,
                            true,
                            true,
                            context.forbiddenPhrases()
                    )
            );
            model = generated.model();
            auditGenerated(AuditResult.SUCCESS, operator, context.project(), null, context.prompt().userPrompt().length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "template_preview_generated", null);
            return new ArticleTemplatePreviewResponse(
                    generated.title(),
                    generated.content(),
                    enrichPromptSnapshot(context.prompt().promptSnapshot(), generated.result(), "AI_PREVIEW"),
                    context.prompt().inputSnapshot(),
                    context.template().getId(),
                    context.version().getId(),
                    context.template().getName(),
                    context.channelGroupCode(),
                    context.channelSubCode(),
                    context.contentStyle(),
                    context.topicAsQuestion(),
                    generated.quality() == null ? null : generated.quality().status(),
                    generated.quality() == null ? List.of() : generated.quality().issues(),
                    unresolvedVariables(context.prompt().systemPrompt(), context.prompt().userPrompt(), generated.content()),
                    model.platformCode(),
                    model.modelId(),
                    model.config().modelName(),
                    generated.result().promptTokens(),
                    generated.result().completionTokens(),
                    generated.result().durationMs()
            );
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_preview_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article template preview LLM invoke failed projectId={} platform={} model={} msg={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getMessage());
            BizException mapped = mapLlmInvokeFailure(ex, "AI article template preview failed");
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_preview_failed", mapped.getCode());
            throw mapped;
        } catch (LlmPermitUnavailableException ex) {
            log.warn("AI article template preview LLM permit busy projectId={} platform={} model={} scope={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getScope());
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_preview_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI 模型当前繁忙，请稍后重试");
        } catch (Exception ex) {
            log.warn("AI article template preview failed projectId={} platform={} model={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex);
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_preview_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article template preview failed");
        }
    }

    private ArticleAiDraftResponse templateGenerateInWorker(ArticleGenerationPromptContextFactory.PromptContextResult context,
                                                            SysUser operator,
                                                            String requestedPlatformCode,
                                                            String requestedModelId) {
        long started = System.nanoTime();
        ArticleModelResolver.ModelSelection model = null;
        try {
            ArticleGenerationEngine.GeneratedArticle generated = articleGenerationEngine.generate(
                    new ArticleGenerationEngine.GenerateInput(
                            context.project(),
                            context.brand(),
                            context.prompt().systemPrompt(),
                            context.prompt().userPrompt(),
                            requestedPlatformCode,
                            requestedModelId,
                            true,
                            true,
                            true,
                            context.forbiddenPhrases()
                    )
            );
            model = generated.model();
            ArticleDraft draft = persistTemplateDraft(context, operator, generated, model);
            auditGenerated(AuditResult.SUCCESS, operator, context.project(), draft.getId(), context.prompt().userPrompt().length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "template_generation_generated", null);
            return new ArticleAiDraftResponse(draft.getId(), STATUS_APPROVED);
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_generation_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article template generation LLM invoke failed projectId={} platform={} model={} msg={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getMessage());
            BizException mapped = mapLlmInvokeFailure(ex, "AI article template generation failed");
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_generation_failed", mapped.getCode());
            throw mapped;
        } catch (LlmPermitUnavailableException ex) {
            log.warn("AI article template generation LLM permit busy projectId={} platform={} model={} scope={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getScope());
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI 模型当前繁忙，请稍后重试");
        } catch (Exception ex) {
            log.warn("AI article template generation failed projectId={} platform={} model={}",
                    context.project().getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex);
            auditGenerated(AuditResult.FAILURE, operator, context.project(), null, context.prompt().userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "template_generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article template generation failed");
        }
    }

    private ArticleAiDraftPreviewResponse previewInWorker(Project project,
                                                          SysUser operator,
                                                          BatchArticlePromptBuilder.PromptBuildResult prompt,
                                                          String inputSnapshot,
                                                          String requestedPlatformCode,
                                                          String requestedModelId,
                                                          boolean allowContactInfo) {
        long started = System.nanoTime();
        ArticleModelResolver.ModelSelection model = null;
        try {
            ArticleGenerationEngine.GeneratedArticle generated = articleGenerationEngine.generate(
                    new ArticleGenerationEngine.GenerateInput(
                            project,
                            resolveBrand(project.getBrandId()),
                            prompt.systemPrompt(),
                            prompt.userPrompt(),
                            requestedPlatformCode,
                            requestedModelId,
                            true,
                            allowContactInfo,
                            false,
                            List.of()
                    )
            );
            model = generated.model();
            String promptSnapshot = enrichPromptSnapshot(prompt.promptSnapshot(), generated.result(), "AI_PREVIEW");
            String responseSnapshot = modelResponseSnapshot(generated.result());
            auditGenerated(AuditResult.SUCCESS, operator, project, null, prompt.userPrompt().length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "preview_generated", null);
            return new ArticleAiDraftPreviewResponse(
                    generated.title(),
                    generated.content(),
                    promptSnapshot,
                    inputSnapshot,
                    responseSnapshot,
                    model.platformCode(),
                    model.modelId(),
                    model.config().modelName()
            );
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, project, null, prompt.userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "preview_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article draft preview LLM invoke failed projectId={} platform={} model={} msg={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getMessage());
            BizException mapped = mapLlmInvokeFailure(ex, "AI article draft preview failed");
            auditGenerated(AuditResult.FAILURE, operator, project, null, prompt.userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "preview_failed", mapped.getCode());
            throw mapped;
        } catch (LlmPermitUnavailableException ex) {
            log.warn("AI article draft preview LLM permit busy projectId={} platform={} model={} scope={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getScope());
            auditGenerated(AuditResult.FAILURE, operator, project, null, prompt.userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "preview_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI 模型当前繁忙，请稍后重试");
        } catch (Exception ex) {
            log.warn("AI article draft preview failed projectId={} platform={} model={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex);
            auditGenerated(AuditResult.FAILURE, operator, project, null, prompt.userPrompt().length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "preview_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article draft preview failed");
        }
    }

    private ArticleAiDraftResponse generateInWorker(Project project,
                                                    Brand brand,
                                                    SysUser operator,
                                                    String articleType,
                                                    String originalPrompt,
                                                    String requestedPlatformCode,
                                                    String requestedModelId) {
        long started = System.nanoTime();
        ArticleModelResolver.ModelSelection model = null;
        try {
            ArticleGenerationEngine.GeneratedArticle generated = articleGenerationEngine.generate(
                    new ArticleGenerationEngine.GenerateInput(
                            project,
                            brand,
                            ARTICLE_PREVIEW_SYSTEM_PROMPT,
                            originalPrompt,
                            requestedPlatformCode,
                            requestedModelId,
                            false,
                            false,
                            false,
                            List.of()
                    )
            );
            model = generated.model();
            ArticleDraft draft = persistDraft(project, operator, articleType, generated.content(), originalPrompt, model, generated.result());
            auditGenerated(AuditResult.SUCCESS, operator, project, draft.getId(), originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), STATUS_APPROVED, null);
            return new ArticleAiDraftResponse(draft.getId(), STATUS_APPROVED);
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "generation_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article draft LLM invoke failed projectId={} platform={} model={} msg={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getMessage());
            BizException mapped = mapLlmInvokeFailure(ex, "AI article draft generation failed");
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "generation_failed", mapped.getCode());
            throw mapped;
        } catch (LlmPermitUnavailableException ex) {
            log.warn("AI article draft generation LLM permit busy projectId={} platform={} model={} scope={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex.getScope());
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI 模型当前繁忙，请稍后重试");
        } catch (Exception ex) {
            log.warn("AI article draft generation failed projectId={} platform={} model={}",
                    project.getId(), platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), ex);
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    platformCode(model, requestedPlatformCode), modelId(model, requestedModelId), elapsedMs(started), "generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article draft generation failed");
        }
    }

    private ArticleDraft persistDraft(Project project, SysUser operator, String articleType, String content,
                                      String originalPrompt, ArticleModelResolver.ModelSelection model, LlmInvokeResult result) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return Objects.requireNonNull(template.execute(status -> {
            String title = extractTitle(content);
            ArticleDraft draft = new ArticleDraft();
            draft.setProjectId(project.getId());
            draft.setArticleType(articleType);
            draft.setTitle(title);
            draft.setStatus(STATUS_APPROVED);
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
            version.setPromptSnapshot(promptSnapshot(originalPrompt, result));
            version.setModelPlatformCode(model.platformCode());
            version.setModelId(model.modelId());
            version.setGeneratedBy(GENERATED_BY_AI);
            version.setCreatedBy(operator.getId());
            articleDraftVersionMapper.insert(version);
            return draft;
        }));
    }

    private ArticleDraft persistTemplateDraft(ArticleGenerationPromptContextFactory.PromptContextResult context,
                                              SysUser operator,
                                              ArticleGenerationEngine.GeneratedArticle generated,
                                              ArticleModelResolver.ModelSelection model) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return Objects.requireNonNull(template.execute(status -> {
            BatchArticlePromptBuilder.PromptBuildInput input = context.promptInput();
            String title = StringUtils.hasText(generated.title()) ? generated.title().trim() : extractTitle(generated.content());

            ArticleDraft draft = new ArticleDraft();
            draft.setBatchId(null);
            draft.setProjectId(context.project().getId());
            draft.setArticleType(input.articleType());
            draft.setContentStyle(context.contentStyle());
            draft.setChannelGroupCode(context.channelGroupCode());
            draft.setChannelSubCode(context.channelSubCode());
            draft.setAgentSiteModule(context.template().getAgentSiteModule());
            draft.setArticleTypeCode(context.template().getArticleTypeCode());
            draft.setPromptTemplateId(context.template().getId());
            draft.setPromptTemplateVersionId(context.version().getId());
            draft.setAllocationMode(ALLOCATION_MODE_CUSTOM);
            draft.setTemplateSource(TEMPLATE_SOURCE_CUSTOM);
            draft.setTopic(input.topic());
            draft.setTopicAsQuestion(context.topicAsQuestion());
            draft.setTitle(title);
            if (ArticlePromptChannels.SELF_MEDIA.equals(context.channelGroupCode())) {
                draft.setCoverImageUrl(coverSelectionService.selectRandomCoverUrl(context.project().getBrandId()));
            }
            draft.setStatus(STATUS_APPROVED);
            draft.setCurrentVersionNo(1);
            draft.setHasRisk(false);
            draft.setRiskSeverity("none");
            draft.setIsDuplicateTitle(false);
            articleDraftMapper.insert(draft);

            ArticleDraftVersion version = new ArticleDraftVersion();
            version.setArticleId(draft.getId());
            version.setVersionNo(1);
            version.setTitle(title);
            version.setContentMarkdown(generated.content());
            version.setPromptSnapshot(enrichPromptSnapshot(context.prompt().promptSnapshot(), generated.result(), "AI_TEMPLATE"));
            version.setInputSnapshot(context.prompt().inputSnapshot());
            version.setModelPlatformCode(model.platformCode());
            version.setModelId(model.modelId());
            version.setGeneratedBy(GENERATED_BY_TEMPLATE_AI);
            version.setCreatedBy(operator.getId());
            articleDraftVersionMapper.insert(version);
            return draft;
        }));
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        return project;
    }

    private String normalizeArticleType(String articleType) {
        String value = StringUtils.hasText(articleType) ? articleType.trim() : "";
        if (!ArticleTypes.isSupported(value)) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Invalid article type");
        }
        return value;
    }

    private Brand resolveBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            return null;
        }
        return brand;
    }

    private BizException mapLlmInvokeFailure(LlmInvokeException ex, String fallbackMessage) {
        if (isLlmAuthFailure(ex)) {
            return new BizException(
                    ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING,
                    "AI 模型认证失败，请检查模型平台 API Key 配置"
            );
        }
        return new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, fallbackMessage);
    }

    private boolean isLlmAuthFailure(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 8; depth++) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("http 401")
                        || normalized.contains("http 403")
                        || normalized.contains("unauthorized")
                        || normalized.contains("forbidden")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String promptSnapshot(String originalPrompt, LlmInvokeResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("prompt", originalPrompt);
        snapshot.put("contentSource", "AI");
        snapshot.put("promptTokens", result.promptTokens());
        snapshot.put("completionTokens", result.completionTokens());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{\"contentSource\":\"AI\"}";
        }
    }

    private String enrichPromptSnapshot(String promptSnapshot, LlmInvokeResult result, String contentSource) {
        Map<String, Object> snapshot = readJson(promptSnapshot);
        snapshot.put("contentSource", contentSource);
        snapshot.put("promptTokens", result.promptTokens());
        snapshot.put("completionTokens", result.completionTokens());
        return writeJson(snapshot);
    }

    private String modelResponseSnapshot(LlmInvokeResult result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("responseText", result.responseText());
        snapshot.put("promptTokens", result.promptTokens());
        snapshot.put("completionTokens", result.completionTokens());
        snapshot.put("durationMs", result.durationMs());
        snapshot.put("retryCount", result.retryCount());
        snapshot.put("callStatus", result.callStatus() == null ? null : result.callStatus().name());
        snapshot.put("platformCode", result.platformCode());
        snapshot.put("platformName", result.platformName());
        snapshot.put("modelId", result.modelId());
        snapshot.put("modelName", result.modelName());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{\"contentSource\":\"AI\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, LinkedHashMap.class);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private List<String> unresolvedVariables(String... values) {
        Pattern pattern = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (!result.contains(variable)) {
                    result.add(variable);
                }
            }
        }
        return result;
    }

    private BatchArticlePromptBuilder.PromptBuildResult buildPreviewPrompt(ArticleAiDraftPreviewRequest req,
                                                                           String articleType,
                                                                           Project project,
                                                                           Brand brand) {
        String topic = trim(req.getTopic());
        return promptBuilder.build(new BatchArticlePromptBuilder.PromptBuildInput(
                project,
                brand,
                resolveBrandStatement(project, brand),
                "manual_preview",
                topic,
                promptBuilder.topicAsQuestion(topic, articleType, 1),
                null,
                null,
                List.of(),
                articleType,
                defaultOption(req.getContentStyle(), DEFAULT_CONTENT_STYLE),
                defaultOption(req.getLength(), DEFAULT_LENGTH),
                buildPreviewExtraPrompt(req, articleType, brand),
                1,
                List.of(),
                null,
                com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes.CUSTOMER,
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
                null
        ), buildContactBlock(articleType, req.getTopic(), brand));
    }

    private String buildPreviewExtraPrompt(ArticleAiDraftPreviewRequest req, String articleType, Brand brand) {
        List<String> parts = new ArrayList<>();
        String tone = defaultOption(req.getTone(), DEFAULT_TONE);
        parts.add("语气：" + TONE_LABELS.getOrDefault(tone, TONE_LABELS.get(DEFAULT_TONE)));
        if (StringUtils.hasText(req.getExtraPrompt())) {
            parts.add("补充要求：" + req.getExtraPrompt().trim());
        }
        if (StringUtils.hasText(req.getReferenceMaterials())) {
            parts.add("参考资料：" + req.getReferenceMaterials().trim());
        }
        return String.join("\n", parts);
    }

    private String resolveBrandStatement(Project project, Brand brand) {
        if (StringUtils.hasText(project.getCustomStatement())) {
            return project.getCustomStatement().trim();
        }
        if (brand == null) {
            return null;
        }
        return buildBrandProfileStatement(brand);
    }

    private String buildBrandProfileStatement(Brand brand) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "品牌定位", brand.getBrandPositioning());
        addPart(parts, "主营业务", brand.getMainBusiness());
        addPart(parts, "核心产品", brand.getCoreProducts());
        addPart(parts, "业务介绍", brand.getBusinessIntro());
        addPart(parts, "资质背书", brand.getBrandQualificationDescription());
        addPart(parts, "案例素材", brand.getBrandCaseDescription());
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private void addPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private String buildContactBlock(String articleType, String topic, Brand brand) {
        if (!shouldIncludeContactInfo(articleType, topic) || brand == null) {
            return "";
        }
        return promptBuilder.buildContactBlock(brand, "full");
    }

    private boolean shouldIncludeContactInfo(String articleType, String topic) {
        return ArticleTypes.FAQ.equals(articleType) && isContactIntentTopic(topic);
    }

    private boolean isContactIntentTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            return false;
        }
        String normalized = topic.trim();
        return CONTACT_INTENT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String defaultOption(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String extractTitle(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.replaceFirst("^#+\\s*", "");
            }
            return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
        }
        return "AI 草稿";
    }

    private String platformCode(ArticleModelResolver.ModelSelection model, String fallback) {
        return model == null ? (StringUtils.hasText(fallback) ? fallback : "unknown") : model.platformCode();
    }

    private String modelId(ArticleModelResolver.ModelSelection model, String fallback) {
        return model == null ? (StringUtils.hasText(fallback) ? fallback : "unknown") : model.modelId();
    }

    private void auditGenerated(AuditResult result, SysUser operator, Project project, Long articleId,
                                int promptLength, String platformCode, String modelId,
                                long durationMs, String status, Integer errorCode) {
        AuditEvent event = new AuditEvent();
        event.setEventType("ARTICLE_AI_DRAFT_GENERATED");
        event.setMode(AuditMode.SYNC);
        event.setSensitive(false);
        event.setResult(result);
        event.setActorId(operator == null ? null : operator.getId());
        event.setBrandId(project == null ? null : project.getBrandId());
        event.setTargetType("ARTICLE");
        event.setTargetId(articleId == null ? null : String.valueOf(articleId));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("promptLength", promptLength);
        detail.put("model", platformCode + "/" + modelId);
        detail.put("durationMs", durationMs);
        detail.put("status", status);
        event.setDetail(detail);
        if (errorCode != null) {
            event.setErrorCode(String.valueOf(errorCode));
            event.setErrorMessage(status);
        }
        auditService.record(event);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

}
