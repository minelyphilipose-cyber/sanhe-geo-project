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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ArticleAiDraftService {

    private static final String STATUS_PENDING_REVIEW = "pending_review";
    private static final String GENERATED_BY_AI = "ai";
    private static final String DEFAULT_CONTENT_STYLE = "wechat";
    private static final String DEFAULT_TONE = "professional";
    private static final String DEFAULT_LENGTH = "medium";
    private static final int ARTICLE_PREVIEW_REQUEST_TIMEOUT_MS = 120_000;

    private static final Map<String, String> CONTENT_STYLE_LABELS = Map.of(
            "wechat", "公众号",
            "toutiao", "头条",
            "douyin_image_text", "抖音图文",
            "zhihu", "知乎",
            "xiaohongshu", "小红书",
            "linkedin", "领英"
    );
    private static final Map<String, String> CONTENT_STYLE_GUIDES = Map.of(
            "wechat", "深度长文，结构完整，适合公众号阅读，重视标题吸引力和段落层次。",
            "toutiao", "资讯密度高，开头直接给结论，段落短，信息点清晰。",
            "douyin_image_text", "钩子开头，语言轻快，适合图文卡片拆分，避免长段落。",
            "zhihu", "问题导向，论据充分，强调分析过程和可信结论。",
            "xiaohongshu", "种草口吻，表达自然，适合加入清单和话题标签。",
            "linkedin", "商务专业，强调洞察、案例和可执行建议。"
    );
    private static final Map<String, String> TONE_LABELS = Map.of(
            "professional", "专业严谨",
            "friendly", "亲切自然",
            "sharp", "观点鲜明",
            "storytelling", "故事化"
    );
    private static final Map<String, String> LENGTH_LABELS = Map.of(
            "short", "短文，约 600 字",
            "medium", "中等篇幅，约 1500 字",
            "long", "长文，约 3000 字"
    );

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
        ModelSelection model = resolveModel(req.getModelPlatformCode(), req.getModelId(), false);

        return CompletableFuture.supplyAsync(
                () -> generateInWorker(project, brand, operator, articleType, originalPrompt, outboundPrompt, model),
                articleAiDraftExecutor
        );
    }

    public CompletableFuture<ArticleAiDraftPreviewResponse> preview(ArticleAiDraftPreviewRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");

        Project project = requireProject(req.getProjectId());
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        rateLimiter.check(operator.getId());

        String articleType = normalizeArticleType(req.getArticleType());
        Brand brand = resolveBrand(project.getBrandId());
        String prompt = buildPreviewPrompt(req, articleType, project, brand);
        String outboundPrompt = promptFilter.filterOutboundPrompt(prompt, project, brand);
        ModelSelection model = resolveModel(req.getModelPlatformCode(), req.getModelId(), true);
        String inputSnapshot = previewInputSnapshot(req, articleType, project, brand);

        return CompletableFuture.supplyAsync(
                () -> previewInWorker(project, operator, prompt, outboundPrompt, inputSnapshot, model),
                articleAiDraftExecutor
        );
    }

    private ArticleAiDraftPreviewResponse previewInWorker(Project project,
                                                          SysUser operator,
                                                          String originalPrompt,
                                                          String outboundPrompt,
                                                          String inputSnapshot,
                                                          ModelSelection model) {
        long started = System.nanoTime();
        try {
            LlmInvokeResult result = llmInvoker.invoke(outboundPrompt, model.config());
            String content = promptFilter.filterGeneratedContent(result.responseText(), project, resolveBrand(project.getBrandId())).trim();
            if (!StringUtils.hasText(content)) {
                throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI generated empty article");
            }
            markdownImageReferenceValidator.validate(project, content);
            String promptSnapshot = promptSnapshot(originalPrompt, result);
            String responseSnapshot = modelResponseSnapshot(result);
            auditGenerated(AuditResult.SUCCESS, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "preview_generated", null);
            return new ArticleAiDraftPreviewResponse(
                    extractTitle(content),
                    content,
                    promptSnapshot,
                    inputSnapshot,
                    responseSnapshot,
                    model.platformCode(),
                    model.modelId(),
                    model.config().modelName()
            );
        } catch (BizException ex) {
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "preview_failed", ex.getCode());
            throw ex;
        } catch (LlmInvokeException ex) {
            log.warn("AI article draft preview LLM invoke failed projectId={} platform={} model={} msg={}",
                    project.getId(), model.platformCode(), model.modelId(), ex.getMessage());
            BizException mapped = mapLlmInvokeFailure(ex, "AI article draft preview failed");
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "preview_failed", mapped.getCode());
            throw mapped;
        } catch (Exception ex) {
            log.warn("AI article draft preview failed projectId={} platform={} model={}",
                    project.getId(), model.platformCode(), model.modelId(), ex);
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "preview_failed",
                    ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED);
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI article draft preview failed");
        }
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
            BizException mapped = mapLlmInvokeFailure(ex, "AI article draft generation failed");
            auditGenerated(AuditResult.FAILURE, operator, project, null, originalPrompt.length(),
                    model.platformCode(), model.modelId(), elapsedMs(started), "generation_failed", mapped.getCode());
            throw mapped;
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

    private ModelSelection resolveModel(String platformCode, String modelId, boolean longForm) {
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
                LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS,
                longForm ? resolveArticleRequestTimeout(config.getTimeoutMs()) : resolveStandardRequestTimeout(config.getTimeoutMs()),
                normalize(config.getMaxRetry(), 2), Math.max(1, normalize(config.getRateLimitQps(), 1)), null, false,
                longForm ? LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS : LlmModelConfig.MAX_REQUEST_TIMEOUT_MS);
        return new ModelSelection(config.getPlatformCode(), resolvedModelId, modelConfig);
    }

    private int resolveStandardRequestTimeout(Integer configuredTimeoutMs) {
        int timeout = normalize(configuredTimeoutMs, LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS);
        return Math.min(timeout, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS);
    }

    private int resolveArticleRequestTimeout(Integer configuredTimeoutMs) {
        int timeout = normalize(configuredTimeoutMs, ARTICLE_PREVIEW_REQUEST_TIMEOUT_MS);
        timeout = Math.max(timeout, ARTICLE_PREVIEW_REQUEST_TIMEOUT_MS);
        return Math.min(timeout, LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
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

    private String previewInputSnapshot(ArticleAiDraftPreviewRequest req,
                                        String articleType,
                                        Project project,
                                        Brand brand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", project.getId());
        snapshot.put("brandId", project.getBrandId());
        snapshot.put("brandName", brand == null ? null : brand.getBrandName());
        snapshot.put("articleType", articleType);
        snapshot.put("contentStyle", normalizeOption(req.getContentStyle()));
        snapshot.put("tone", normalizeOption(req.getTone()));
        snapshot.put("length", normalizeOption(req.getLength()));
        snapshot.put("topic", trim(req.getTopic()));
        snapshot.put("extraPrompt", trim(req.getExtraPrompt()));
        snapshot.put("referenceMaterials", trim(req.getReferenceMaterials()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String buildPreviewPrompt(ArticleAiDraftPreviewRequest req,
                                      String articleType,
                                      Project project,
                                      Brand brand) {
        String contentStyle = normalizeOption(req.getContentStyle());
        String tone = normalizeOption(req.getTone());
        String length = normalizeOption(req.getLength());
        String styleLabel = CONTENT_STYLE_LABELS.getOrDefault(contentStyle, CONTENT_STYLE_LABELS.get(DEFAULT_CONTENT_STYLE));
        String styleGuide = CONTENT_STYLE_GUIDES.getOrDefault(contentStyle, CONTENT_STYLE_GUIDES.get(DEFAULT_CONTENT_STYLE));
        String toneLabel = TONE_LABELS.getOrDefault(tone, TONE_LABELS.get(DEFAULT_TONE));
        String lengthLabel = LENGTH_LABELS.getOrDefault(length, LENGTH_LABELS.get(DEFAULT_LENGTH));

        return """
                # 角色
                你是一名中文 GEO 内容文章写作助手，负责为品牌项目生成可审核的 Markdown 文章草稿。

                # 项目信息
                - 项目 ID：%s
                - 项目名称：%s
                - 品牌：%s
                - 对外公开电话：%s
                - 对外公开地址：%s
                - 文章类型：%s

                # 写作要求
                - 内容风格：%s
                - 风格说明：%s
                - 语气：%s
                - 篇幅：%s
                - 选题：%s

                # 补充要求
                %s

                # 参考资料
                %s

                # 输出要求
                - 只输出 Markdown 正文，不要解释提示词。
                - 第一行必须是一级标题，格式为 "# 标题"。
                - 正文至少包含 3 个二级标题，格式为 "## 小标题"。
                - 不要编造联系方式、价格、资质、客户案例或不可验证数据。
                - 避免空泛套话，给出具体分析和可执行建议。
                """.formatted(
                project.getId(),
                nullToDash(project.getProjectName()),
                brand == null ? "-" : nullToDash(brand.getBrandName()),
                brand == null ? "-" : nullToDash(brand.getPublicPhone()),
                brand == null ? "-" : nullToDash(brand.getPublicAddress()),
                articleType,
                styleLabel,
                styleGuide,
                toneLabel,
                lengthLabel,
                trim(req.getTopic()),
                StringUtils.hasText(req.getExtraPrompt()) ? req.getExtraPrompt().trim() : "无",
                StringUtils.hasText(req.getReferenceMaterials()) ? req.getReferenceMaterials().trim() : "无"
        );
    }

    private String normalizeOption(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
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
