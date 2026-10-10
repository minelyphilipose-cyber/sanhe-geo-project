package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArticleGenerationEngine {

    private static final Pattern MARKDOWN_TITLE_PREFIX = Pattern.compile("^(#+)\\s*");

    private final LlmCallFacade llmCallFacade;
    private final ArticleModelResolver modelResolver;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final BatchArticleQualityChecker qualityChecker;
    private final ArticleTitleDuplicateChecker titleDuplicateChecker;
    private final XiaohongshuArticleStructureValidator xiaohongshuStructureValidator;

    @Value("${geo.llm.routing.article-request-timeout-ms:300000}")
    private int articleRequestTimeoutMs = LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS;

    public GeneratedArticle generate(GenerateInput input) throws LlmInvokeException {
        boolean sohu = input.structureValidationContext() != null && SohuArticlePolicies.applies(
                input.structureValidationContext().channelGroupCode(), input.structureValidationContext().channelSubCode());
        String outboundPrompt = sohu
                ? promptFilter.filterOutboundPrompt(input.userPrompt(), input.project(), input.brand(), false, true)
                : promptFilter.filterOutboundPrompt(
                input.userPrompt(),
                input.project(),
                input.brand(),
                input.allowContactInfo()
        );
        GenerationCall call = invokeModel(input, outboundPrompt);
        LlmInvokeResult result = call.result();
        ArticleModelResolver.ModelSelection model = call.model();
        String content = normalizeContent(sohu
                ? promptFilter.filterGeneratedContent(result.responseText(), input.project(), input.brand(), false, true)
                : promptFilter.filterGeneratedContent(
                result.responseText(),
                input.project(),
                input.brand(),
                input.allowContactInfo()
        ));
        if (!StringUtils.hasText(content)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI generated empty article");
        }
        boolean douyin = isDouyin(input.structureValidationContext());
        boolean bodyRewritten = false;
        if (douyin) {
            validateDouyinTitle(content, input.maxTitleChars());
            String issue = DouyinArticleContentPolicies.lengthIssue(content);
            if (issue != null) {
                String originalTitle = extractTitle(content);
                GenerateInput rewriteInput = douyinBodyRewriteInput(input, model, outboundPrompt, content, issue);
                GenerationCall rewriteCall = invokeModel(rewriteInput,
                        promptFilter.filterOutboundPrompt(rewriteInput.userPrompt(), input.project(), input.brand(), input.allowContactInfo()));
                String rewritten = normalizeContent(promptFilter.filterGeneratedContent(
                        rewriteCall.result().responseText(), input.project(), input.brand(), input.allowContactInfo()));
                validateDouyinTitle(rewritten, input.maxTitleChars());
                rewritten = rewritten.replaceFirst("^# [^\\r\\n]+", Matcher.quoteReplacement("# " + originalTitle));
                String rewriteIssue = DouyinArticleContentPolicies.lengthIssue(rewritten);
                if (rewriteIssue != null) {
                    throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                            rewriteIssue + "；自动重写后篇幅仍不符合要求，请重新生成，不会截断正文");
                }
                content = rewritten;
                result = combineRewriteUsage(result, rewriteCall.result());
                bodyRewritten = true;
            }
        }
        if (sohu) SohuArticlePolicies.validateTitle(extractTitle(content), input.project(), input.brand());
        boolean toutiao = input.structureValidationContext() != null && ToutiaoArticleTitlePolicies.applies(
                input.structureValidationContext().channelGroupCode(), input.structureValidationContext().channelSubCode());
        boolean titleRewritten = false;
        if (toutiao) {
            String title = standaloneToutiaoTitle(content);
            String issue = ToutiaoArticleTitlePolicies.issue(title, input.project(), input.brand(),
                    ArticlePromptChannels.SELF_MEDIA_MAX_TITLE_CHARS);
            if (issue != null) {
                GenerateInput rewriteInput = titleRewriteInput(input, model, content, issue);
                GenerationCall titleCall = invokeModel(rewriteInput,
                        promptFilter.filterOutboundPrompt(rewriteInput.userPrompt(),
                                input.project(), input.brand(), input.allowContactInfo()));
                String rewritten = normalizeContent(promptFilter.filterGeneratedContent(
                        titleCall.result().responseText(), input.project(), input.brand(), input.allowContactInfo()));
                rewritten = rewritten.replaceFirst("^#\\s+", "").strip();
                String rewriteIssue = rewritten.contains("\n") || rewritten.contains("\r")
                        ? "重拟结果包含正文或多个标题"
                        : ToutiaoArticleTitlePolicies.issue(rewritten, input.project(), input.brand(),
                                ArticlePromptChannels.SELF_MEDIA_MAX_TITLE_CHARS);
                if (rewriteIssue != null) {
                    throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                            "头条标题重拟后仍不符合要求：" + rewriteIssue + "；请重新生成完整标题，不会强行截断");
                }
                content = content.replaceFirst("^# [^\\r\\n]+", Matcher.quoteReplacement("# " + rewritten));
                result = combineRewriteUsage(result, titleCall.result());
                titleRewritten = true;
            }
        }
        TitleLimitResult titleLimit = toutiao ? new TitleLimitResult(content, false) : limitTitle(content, input.maxTitleChars());
        content = titleLimit.content();
        ContentLimitResult contentLimit = douyin ? new ContentLimitResult(content, false) : limitBodyContent(content, input.maxContentChars());
        content = contentLimit.content();
        markdownImageReferenceValidator.validate(input.project(), content);
        BatchArticleQualityChecker.QualityResult quality = null;
        String title = extractTitle(content);
        if (input.checkQuality()) {
            quality = qualityChecker.check(content, input.brand(), input.forbiddenPhrases());
            StructureValidationContext structureContext = input.structureValidationContext();
            if (structureContext != null
                    && structureContext.neutralEducationMode()
                    && ArticlePromptChannels.SELF_MEDIA.equals(structureContext.channelGroupCode())
                    && "xiaohongshu".equals(ArticlePromptChannels.canonicalSubCode(
                    structureContext.channelGroupCode(), structureContext.channelSubCode()))) {
                for (XiaohongshuArticleStructureValidator.Violation violation
                        : xiaohongshuStructureValidator.validate(content, input.project(), input.brand(),
                        structureContext.neutralEducationMode())) {
                    quality = qualityChecker.withHardIssue(quality, violation.type(), violation.message());
                }
            }
            if (titleLimit.truncated()) {
                quality = qualityChecker.withWarning(quality, "title_truncated",
                        "标题超过" + input.maxTitleChars() + "字，已自动截短");
            }
            if (titleRewritten) {
                quality = qualityChecker.withWarning(quality, "title_rewritten",
                        "头条标题已按地域、A 类问题核心对象和具体关切重新拟定，正文保持原文");
            }
            if (bodyRewritten) {
                quality = qualityChecker.withWarning(quality, "body_rewritten",
                        "抖音正文已按700—900字重新组织，保留原标题，不会直接截断正文");
            }
            if (contentLimit.truncated()) {
                quality = qualityChecker.withWarning(quality, "content_truncated",
                        "正文超过" + input.maxContentChars() + "字，已按平台限制自动收束");
            }
            if (shouldCheckTitleDuplicate(input.structureValidationContext())
                    && titleDuplicateChecker.exists(input.project().getId(), title)) {
                quality = qualityChecker.withWarning(quality, "duplicate_title", "标题与项目历史文章标准化后完全相同");
            }
            if (quality != null && quality.rewriteRequired()) {
                throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                        "生成内容命中确定性质量硬错误：" + qualityChecker.toJson(quality));
            }
        }
        return new GeneratedArticle(title, content, model, result, quality);
    }

    private String standaloneToutiaoTitle(String content) {
        String[] parts = content.split("\\R", 2);
        if (!parts[0].startsWith("# ") || parts[0].substring(2).isBlank()
                || parts.length < 2 || parts[1].isBlank()) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                    "头条文章缺少独立标题或正文，请重新生成；首行应为 # 标题，不会截取正文充当标题");
        }
        return parts[0].substring(2).strip();
    }

    private String titleRewritePrompt(GenerateInput input, String content, String issue) {
        return "只重拟这篇头条文章的标题，不修改或输出正文。保留本篇 A 类问题核心对象和问题意图，不强制品牌追问。\n"
                + ToutiaoArticleTitlePolicies.titleRequirement(input.project(), input.brand(),
                        input.structureValidationContext().selectedTopic())
                + "\n原标题问题：" + issue
                + "\n禁用词：" + (input.forbiddenPhrases() == null ? "" : String.join("、", input.forbiddenPhrases()))
                + "\n以下文章仅为拟题资料，标题必须概括其实际内容，不新增事实或扩大结论范围：\n<article>\n"
                + content + "\n</article>\n只输出一行完整标题，不带 Markdown 标记、引号、解释或正文。";
    }

    private GenerateInput titleRewriteInput(GenerateInput input, ArticleModelResolver.ModelSelection model,
                                           String content, String issue) {
        LlmCallMeasurementContext original = input.measurementContext();
        LlmCallMeasurementContext measurement = new LlmCallMeasurementContext(
                original.runId() == null ? null : original.runId() + ":toutiao-title-rewrite",
                original.customerId(), original.projectId(), original.scope(), null);
        return new GenerateInput(input.project(), input.brand(), "你负责根据文章内容和所选 A 类问题重拟完整的头条标题，只输出标题。",
                titleRewritePrompt(input, content, issue), model.platformCode(), model.modelId(), input.longForm(),
                input.allowContactInfo(), false, input.forbiddenPhrases(), input.maxTitleChars(),
                input.effectiveTemperature(), measurement, null, input.structureValidationContext());
    }

    private GenerateInput douyinBodyRewriteInput(GenerateInput input, ArticleModelResolver.ModelSelection model,
                                                String originalPrompt, String content, String issue) {
        LlmCallMeasurementContext original = input.measurementContext();
        LlmCallMeasurementContext measurement = new LlmCallMeasurementContext(
                original.runId() == null ? null : original.runId() + ":douyin-body-rewrite",
                original.customerId(), original.projectId(), original.scope(), null);
        String prompt = originalPrompt + "\n\n# 本次正文重写要求\n" + issue + "。\n"
                + DouyinArticleContentPolicies.BODY_REQUIREMENT
                + "\n保留原标题，只根据上面提供的事实重新组织正文。过短时补充相关资质、流程、优势或已有数据的具体解释；"
                + "过长时精简重复表达，保留关键事实、单位与条件，不直接截尾，不新增事实。\n<original_article>\n"
                + content + "\n</original_article>\n只输出完整 Markdown 文章，首行 # " + extractTitle(content) + "，空行后为700—900字正文。";
        return new GenerateInput(input.project(), input.brand(), input.systemPrompt(), prompt,
                model.platformCode(), model.modelId(), input.longForm(), input.allowContactInfo(), false,
                input.forbiddenPhrases(), input.maxTitleChars(), input.effectiveTemperature(), measurement,
                input.maxContentChars(), input.structureValidationContext());
    }

    private LlmInvokeResult combineRewriteUsage(LlmInvokeResult original, LlmInvokeResult rewrite) {
        return new LlmInvokeResult(original.responseText(),
                (original.promptTokens() == null ? 0 : original.promptTokens()) + (rewrite.promptTokens() == null ? 0 : rewrite.promptTokens()),
                (original.completionTokens() == null ? 0 : original.completionTokens()) + (rewrite.completionTokens() == null ? 0 : rewrite.completionTokens()),
                (original.durationMs() == null ? 0L : original.durationMs()) + (rewrite.durationMs() == null ? 0L : rewrite.durationMs()),
                original.retryCount(), original.callStatus(), original.platformCode(), original.platformName(), original.modelId(), original.modelName());
    }

    private GenerationCall invokeModel(GenerateInput input, String outboundPrompt) throws LlmInvokeException {
        if (StringUtils.hasText(input.modelPlatformCode()) || StringUtils.hasText(input.modelId())) {
            ArticleModelResolver.ModelSelection model = modelResolver.resolve(
                    input.modelPlatformCode(),
                    input.modelId(),
                    input.systemPrompt(),
                    input.longForm(),
                    input.effectiveTemperature()
            );
            return new GenerationCall(model, llmCallFacade.execute(
                    LlmCallRequest.direct(outboundPrompt, model.config())
                            .withMeasurementContext(input.measurementContext())).invokeResult());
        }
        try {
            LlmCallResult callResult = llmCallFacade.execute(LlmCallRequest.routed(new LlmRouteRequest(
                    LlmFeature.ARTICLE,
                    input.systemPrompt(),
                    outboundPrompt,
                    input.effectiveTemperature(),
                    LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS,
                    resolveRequestTimeout(input.longForm()),
                    input.longForm() ? LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS : LlmModelConfig.MAX_REQUEST_TIMEOUT_MS,
                    0,
                    null,
                    false,
                    1,
                    0,
                    List.of()
            )).withMeasurementContext(input.measurementContext()));
            LlmRouteResult routed = callResult.routeResult();
            return new GenerationCall(
                    new ArticleModelResolver.ModelSelection(routed.platformCode(), routed.modelId(), null),
                    routed.invokeResult()
            );
        } catch (LlmRouteException ex) {
            throw new LlmInvokeException("LLM route failed: " + ex.getMessage(), ex);
        }
    }

    private int resolveRequestTimeout(boolean longForm) {
        if (!longForm) {
            return LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS;
        }
        return Math.min(
                Math.max(articleRequestTimeoutMs, LlmModelConfig.MAX_REQUEST_TIMEOUT_MS),
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
    }

    static boolean shouldCheckTitleDuplicate(StructureValidationContext context) {
        return context == null
                || !ArticlePromptChannels.SELF_MEDIA.equals(context.channelGroupCode())
                || !"douyin".equals(ArticlePromptChannels.canonicalSubCode(
                context.channelGroupCode(), context.channelSubCode()));
    }

    private static boolean isDouyin(StructureValidationContext context) {
        return context != null && ArticlePromptChannels.SELF_MEDIA.equals(context.channelGroupCode())
                && "douyin".equals(ArticlePromptChannels.canonicalSubCode(
                context.channelGroupCode(), context.channelSubCode()));
    }

    // A prose opening is not a title. Reject before either limiter can discard it.
    static void validateDouyinTitle(String content, Integer maxChars) {
        String[] parts = content.strip().split("\\R", 2);
        String first = parts[0].strip();
        if (!first.startsWith("# ") || first.substring(2).isBlank()
                || parts.length < 2 || parts[1].isBlank()) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                    "抖音文章缺少独立标题或正文，请重新生成；首行应为 # 标题，不会截取正文充当标题");
        }
        String title = first.substring(2).strip();
        if (maxChars != null && title.codePointCount(0, title.length()) > maxChars) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                    "抖音标题超过" + maxChars + "字，请重新生成完整短标题，不会强行截断");
        }
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.trim()
                .replaceFirst("^```(?:markdown)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private TitleLimitResult limitTitle(String content, Integer maxTitleChars) {
        if (!StringUtils.hasText(content) || maxTitleChars == null || maxTitleChars <= 0) {
            return new TitleLimitResult(content, false);
        }
        String[] lines = content.split("\\r?\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            Matcher matcher = MARKDOWN_TITLE_PREFIX.matcher(line);
            boolean hasMarkdownPrefix = matcher.find();
            String prefix = hasMarkdownPrefix ? matcher.group(1) + " " : "";
            String title = hasMarkdownPrefix ? line.substring(matcher.end()).trim() : line;
            if (title.codePointCount(0, title.length()) <= maxTitleChars) {
                return new TitleLimitResult(content, false);
            }
            int end = title.offsetByCodePoints(0, maxTitleChars);
            String shortenedTitle = trimTrailingTitlePunctuation(title.substring(0, end));
            lines[index] = prefix + shortenedTitle;
            return new TitleLimitResult(String.join("\n", Arrays.asList(lines)).trim(), true);
        }
        return new TitleLimitResult(content, false);
    }

    private String trimTrailingTitlePunctuation(String title) {
        String value = title.replaceFirst("[，、：；。！？,.!?:;—\\-\\s]+$", "").trim();
        return StringUtils.hasText(value) ? value : title.trim();
    }

    private ContentLimitResult limitBodyContent(String content, Integer maxContentChars) {
        if (!StringUtils.hasText(content) || maxContentChars == null || maxContentChars <= 0) {
            return new ContentLimitResult(content, false);
        }
        int firstLineEnd = content.indexOf('\n');
        String titleLine = firstLineEnd < 0 ? content.trim() : content.substring(0, firstLineEnd).trim();
        String body = firstLineEnd < 0 ? "" : content.substring(firstLineEnd + 1).trim();
        if (body.codePointCount(0, body.length()) <= maxContentChars) {
            return new ContentLimitResult(content, false);
        }
        int end = body.offsetByCodePoints(0, maxContentChars);
        String candidate = body.substring(0, end);
        int minimumBoundary = candidate.length() * 2 / 3;
        int boundary = -1;
        for (int index = minimumBoundary; index < candidate.length(); index++) {
            char current = candidate.charAt(index);
            if ("。！？!?；;\n".indexOf(current) >= 0) {
                boundary = index + 1;
            }
        }
        String shortenedBody = (boundary > 0 ? candidate.substring(0, boundary) : candidate)
                .replaceFirst("[，、：,:\\s]+$", "")
                .trim();
        String shortened = StringUtils.hasText(shortenedBody)
                ? titleLine + "\n\n" + shortenedBody
                : titleLine;
        return new ContentLimitResult(shortened.trim(), true);
    }

    public String extractTitle(String content) {
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

    public record GenerateInput(Project project,
                                Brand brand,
                                String systemPrompt,
                                String userPrompt,
                                String modelPlatformCode,
                                String modelId,
                                boolean longForm,
                                boolean allowContactInfo,
                                boolean checkQuality,
                                 List<String> forbiddenPhrases,
                                 Integer maxTitleChars,
                                 double effectiveTemperature,
                                 LlmCallMeasurementContext measurementContext,
                                 Integer maxContentChars,
                                 StructureValidationContext structureValidationContext) {
        public GenerateInput(Project project,
                             Brand brand,
                             String systemPrompt,
                             String userPrompt,
                             String modelPlatformCode,
                             String modelId,
                             boolean longForm,
                             boolean allowContactInfo,
                             boolean checkQuality,
                             List<String> forbiddenPhrases,
                             Integer maxTitleChars,
                             double effectiveTemperature,
                             LlmCallMeasurementContext measurementContext,
                             Integer maxContentChars) {
            this(project, brand, systemPrompt, userPrompt, modelPlatformCode, modelId, longForm,
                    allowContactInfo, checkQuality, forbiddenPhrases, maxTitleChars, effectiveTemperature,
                    measurementContext, maxContentChars, null);
        }

        public GenerateInput(Project project,
                             Brand brand,
                             String systemPrompt,
                             String userPrompt,
                             String modelPlatformCode,
                             String modelId,
                             boolean longForm,
                             boolean allowContactInfo,
                             boolean checkQuality,
                             List<String> forbiddenPhrases,
                             Integer maxTitleChars,
                             double effectiveTemperature,
                             Integer maxContentChars,
                             StructureValidationContext structureValidationContext) {
            this(project, brand, systemPrompt, userPrompt, modelPlatformCode, modelId, longForm,
                    allowContactInfo, checkQuality, forbiddenPhrases, maxTitleChars, effectiveTemperature,
                    LlmCallMeasurementContext.empty(), maxContentChars, structureValidationContext);
        }

        public GenerateInput(Project project,
                             Brand brand,
                             String systemPrompt,
                             String userPrompt,
                             String modelPlatformCode,
                             String modelId,
                             boolean longForm,
                             boolean allowContactInfo,
                             boolean checkQuality,
                             List<String> forbiddenPhrases,
                             Integer maxTitleChars,
                             double effectiveTemperature,
                             LlmCallMeasurementContext measurementContext) {
            this(project, brand, systemPrompt, userPrompt, modelPlatformCode, modelId, longForm,
                    allowContactInfo, checkQuality, forbiddenPhrases, maxTitleChars, effectiveTemperature,
                    measurementContext, null, null);
        }

        public GenerateInput(Project project,
                             Brand brand,
                             String systemPrompt,
                             String userPrompt,
                             String modelPlatformCode,
                             String modelId,
                             boolean longForm,
                             boolean allowContactInfo,
                             boolean checkQuality,
                             List<String> forbiddenPhrases,
                             Integer maxTitleChars,
                             double effectiveTemperature,
                             Integer maxContentChars) {
            this(project, brand, systemPrompt, userPrompt, modelPlatformCode, modelId, longForm,
                    allowContactInfo, checkQuality, forbiddenPhrases, maxTitleChars, effectiveTemperature,
                    LlmCallMeasurementContext.empty(), maxContentChars, null);
        }

        public GenerateInput(Project project,
                             Brand brand,
                             String systemPrompt,
                             String userPrompt,
                             String modelPlatformCode,
                             String modelId,
                             boolean longForm,
                             boolean allowContactInfo,
                             boolean checkQuality,
                             List<String> forbiddenPhrases,
                             Integer maxTitleChars,
                             double effectiveTemperature) {
            this(project, brand, systemPrompt, userPrompt, modelPlatformCode, modelId, longForm,
                    allowContactInfo, checkQuality, forbiddenPhrases, maxTitleChars, effectiveTemperature,
                    LlmCallMeasurementContext.empty(), null, null);
        }

        public GenerateInput {
            measurementContext = measurementContext == null
                    ? LlmCallMeasurementContext.empty()
                    : measurementContext;
        }
    }

    public record StructureValidationContext(String channelGroupCode,
                                             String channelSubCode,
                                             boolean neutralEducationMode,
                                             String selectedTopic) {
        public StructureValidationContext(String channelGroupCode, String channelSubCode, boolean neutralEducationMode) {
            this(channelGroupCode, channelSubCode, neutralEducationMode, null);
        }
    }

    public record GeneratedArticle(String title,
                                   String content,
                                   ArticleModelResolver.ModelSelection model,
                                   LlmInvokeResult result,
                                   BatchArticleQualityChecker.QualityResult quality) {
    }

    private record GenerationCall(ArticleModelResolver.ModelSelection model,
                                  LlmInvokeResult result) {
    }

    private record TitleLimitResult(String content, boolean truncated) {
    }

    private record ContentLimitResult(String content, boolean truncated) {
    }
}
