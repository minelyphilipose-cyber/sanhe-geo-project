package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.content.ContentErrorCodes;
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

    @Value("${geo.llm.routing.article-request-timeout-ms:300000}")
    private int articleRequestTimeoutMs = LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS;

    public GeneratedArticle generate(GenerateInput input) throws LlmInvokeException {
        String outboundPrompt = promptFilter.filterOutboundPrompt(
                input.userPrompt(),
                input.project(),
                input.brand(),
                input.allowContactInfo()
        );
        GenerationCall call = invokeModel(input, outboundPrompt);
        LlmInvokeResult result = call.result();
        ArticleModelResolver.ModelSelection model = call.model();
        String content = normalizeContent(promptFilter.filterGeneratedContent(
                result.responseText(),
                input.project(),
                input.brand(),
                input.allowContactInfo()
        ));
        if (!StringUtils.hasText(content)) {
            throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED, "AI generated empty article");
        }
        TitleLimitResult titleLimit = limitTitle(content, input.maxTitleChars());
        content = titleLimit.content();
        markdownImageReferenceValidator.validate(input.project(), content);
        BatchArticleQualityChecker.QualityResult quality = null;
        String title = extractTitle(content);
        if (input.checkQuality()) {
            quality = qualityChecker.check(content, input.brand(), input.forbiddenPhrases());
            if (titleLimit.truncated()) {
                quality = qualityChecker.withWarning(quality, "title_truncated",
                        "标题超过" + input.maxTitleChars() + "字，已自动截短");
            }
            if (titleDuplicateChecker.exists(input.project().getId(), title)) {
                quality = qualityChecker.withWarning(quality, "duplicate_title", "标题与项目历史文章标准化后完全相同");
            }
            if (quality != null && quality.rewriteRequired()) {
                throw new BizException(ContentErrorCodes.ARTICLE_AI_DRAFT_GENERATE_FAILED,
                        "生成内容命中确定性质量硬错误：" + qualityChecker.toJson(quality));
            }
        }
        return new GeneratedArticle(title, content, model, result, quality);
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
            return new GenerationCall(model, llmCallFacade.execute(LlmCallRequest.direct(outboundPrompt, model.config())).invokeResult());
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
            )));
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
                                double effectiveTemperature) {
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
}
