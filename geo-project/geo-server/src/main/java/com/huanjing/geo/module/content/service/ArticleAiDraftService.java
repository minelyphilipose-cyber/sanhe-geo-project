package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.*;
import com.huanjing.geo.module.audit.*;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticleTypes;
import com.huanjing.geo.module.content.dto.*;
import com.huanjing.geo.module.content.entity.*;
import com.huanjing.geo.module.content.mapper.*;
import com.huanjing.geo.module.customer.access.*;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.*;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ArticleAiDraftService {

    private static final String STATUS_PENDING_REVIEW = "pending_review";
    private static final String GENERATED_BY_AI = "ai";

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ArticleDraftVersionMapper articleDraftVersionMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final PlatformCredentialService platformCredentialService;
    private final LlmInvoker llmInvoker;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final ArticleAiDraftRateLimiter rateLimiter;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Executor articleAiDraftExecutor;

    public ArticleAiDraftService(ProjectMapper projectMapper, BrandMapper brandMapper,
                                 ArticleDraftMapper articleDraftMapper, ArticleDraftVersionMapper articleDraftVersionMapper,
                                 AiPlatformConfigMapper aiPlatformConfigMapper, CurrentUserService currentUserService,
                                 BrandAccessService brandAccessService, PlatformCredentialService platformCredentialService,
                                 LlmInvoker llmInvoker, MarkdownImageReferenceValidator markdownImageReferenceValidator,
                                 ArticleAiDraftPromptFilter promptFilter, ArticleAiDraftRateLimiter rateLimiter,
                                 AuditService auditService, ObjectMapper objectMapper,
                                 PlatformTransactionManager transactionManager,
                                 @Qualifier("articleAiDraftExecutor") Executor articleAiDraftExecutor) {
        this.projectMapper = projectMapper; this.brandMapper = brandMapper;
        this.articleDraftMapper = articleDraftMapper; this.articleDraftVersionMapper = articleDraftVersionMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper; this.currentUserService = currentUserService;
        this.brandAccessService = brandAccessService; this.platformCredentialService = platformCredentialService;
        this.llmInvoker = llmInvoker; this.markdownImageReferenceValidator = markdownImageReferenceValidator;
        this.promptFilter = promptFilter; this.rateLimiter = rateLimiter;
        this.auditService = auditService; this.objectMapper = objectMapper;
        this.transactionManager = transactionManager; this.articleAiDraftExecutor = articleAiDraftExecutor;
    }

    public CompletableFuture<ArticleAiDraftResponse> generate(ArticleAiDraftRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");

        Project project = requireProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        rateLimiter.check(operator.getId());

        String articleType = normalizeArticleType(req.getArticleType());
        String originalPrompt = req.getPrompt().trim();
        Brand brand = resolveBrand(project.getBrandId());
        String outboundPrompt = promptFilter.filterOutboundPrompt(originalPrompt, project, brand);
        ModelSelection model = resolveModel(req.getModelPlatformCode(), req.getModelId());

        return CompletableFuture.supplyAsync(
                () -> generateInWorker(project, brand, operator, articleType, originalPrompt, outboundPrompt, model),
                articleAiDraftExecutor
        );
    }

    private ArticleAiDraftResponse generateInWorker(Project project,
                                                    Brand brand,
                                                    SysUser operator,
                                                    String articleType,
                                                    String originalPrompt,
                                                    String outboundPrompt,
                                                    ModelSelection model) {
        long started = System.nanoTime();
        try {
            LlmInvokeResult result = llmInvoker.invoke(outboundPrompt, model.config());
            String content = promptFilter.filterGeneratedContent(result.responseText(), project, brand).trim();
            if (!StringUtils.hasText(content)) {
                throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI generated empty article");
            }
            markdownImageReferenceValidator.validate(project, content);

            ArticleDraft draft = persistDraft(project, operator, articleType, content, originalPrompt, model, result);
            auditGenerated(AuditResult.SUCCESS, operator, project, draft.getId(), originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), STATUS_PENDING_REVIEW, null);
            return new ArticleAiDraftResponse(draft.getId(), STATUS_PENDING_REVIEW);
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "generation_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article draft LLM invoke failed projectId={} platform={} model={} msg={}",
                    project.getId(), model.platformCode(), model.modelId(), ex.getMessage());
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article draft generation failed");
        } catch (Exception ex) {
            log.warn("AI article draft generation failed projectId={} platform={} model={}",
                    project.getId(), model.platformCode(), model.modelId(), ex);
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "generation_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article draft generation failed");
        }
    }

    private ArticleDraft persistDraft(Project project, SysUser operator, String articleType, String content,
                                      String originalPrompt, ModelSelection model, LlmInvokeResult result) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return Objects.requireNonNull(template.execute(status -> {
            String title = extractTitle(content);
            ArticleDraft draft = new ArticleDraft();
            draft.setProjectId(project.getId());
            draft.setArticleType(articleType);
            draft.setTitle(title);
            draft.setStatus(STATUS_PENDING_REVIEW);
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

    private ModelSelection resolveModel(String platformCode, String modelId) {
        LambdaQueryWrapper<AiPlatformConfig> wrapper = new LambdaQueryWrapper<AiPlatformConfig>()
                .eq(AiPlatformConfig::getEnabled, true)
                .eq(AiPlatformConfig::getEnabledForArticle, true)
                .orderByAsc(AiPlatformConfig::getId);
        if (StringUtils.hasText(platformCode)) {
            wrapper.eq(AiPlatformConfig::getPlatformCode, platformCode.trim());
        }
        if (StringUtils.hasText(modelId)) {
            String trimmedModelId = modelId.trim();
            wrapper.and(w -> w.eq(AiPlatformConfig::getModelId, trimmedModelId)
                    .or()
                    .eq(AiPlatformConfig::getLowModelId, trimmedModelId));
        }
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(wrapper.last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getApiUrl())) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        String resolvedModelId = StringUtils.hasText(modelId)
                ? modelId.trim()
                : (StringUtils.hasText(config.getModelId()) ? config.getModelId().trim() : config.getLowModelId());
        if (!StringUtils.hasText(resolvedModelId)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        String apiKey = platformCredentialService.resolveApiKey(
                config.getPlatformCode(), config.getPrimaryKeyRef(), config.getApiKey()
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_CONFIG_MISSING, "AI article model config missing");
        }
        LlmModelConfig modelConfig = new LlmModelConfig(config.getPlatformCode(), config.getPlatformName(),
                resolvedModelId, resolveModelDisplayName(config, resolvedModelId), config.getApiUrl(), apiKey,
                "你是一个中文 GEO 内容文章写作助手。只输出完整 Markdown 正文，不输出提示词解释。", 0.4D,
                LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS, LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS,
                normalize(config.getMaxRetry(), 2), Math.max(1, normalize(config.getRateLimitQps(), 1)), null, false);
        return new ModelSelection(config.getPlatformCode(), resolvedModelId, modelConfig);
    }

    private String resolveModelDisplayName(AiPlatformConfig config, String modelId) {
        if (StringUtils.hasText(config.getModelName()) && modelId.equals(config.getModelId())) {
            return config.getModelName().trim();
        }
        return modelId;
    }

    private int normalize(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
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

    private record ModelSelection(String platformCode, String modelId, LlmModelConfig config) {
    }
}
