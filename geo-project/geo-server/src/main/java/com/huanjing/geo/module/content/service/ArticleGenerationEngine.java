package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleGenerationEngine {

    private static final int ARTICLE_REQUEST_TIMEOUT_MS = 120_000;
    private static final double ARTICLE_TEMPERATURE = 0.4D;

    private final LlmInvoker llmInvoker;
    private final ArticleModelResolver modelResolver;
    private final LlmPlatformRouter llmPlatformRouter;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final BatchArticleQualityChecker qualityChecker;

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
        markdownImageReferenceValidator.validate(input.project(), content);
        BatchArticleQualityChecker.QualityResult quality = null;
        if (input.checkQuality()) {
            quality = qualityChecker.check(content, input.brand(), input.forbiddenPhrases());
        }
        return new GeneratedArticle(extractTitle(content), content, model, result, quality);
    }

    private GenerationCall invokeModel(GenerateInput input, String outboundPrompt) throws LlmInvokeException {
        if (StringUtils.hasText(input.modelPlatformCode()) || StringUtils.hasText(input.modelId())) {
            ArticleModelResolver.ModelSelection model = modelResolver.resolve(
                    input.modelPlatformCode(),
                    input.modelId(),
                    input.systemPrompt(),
                    input.longForm()
            );
            return new GenerationCall(model, llmInvoker.invoke(outboundPrompt, model.config()));
        }
        try {
            LlmRouteResult routed = llmPlatformRouter.invoke(new LlmRouteRequest(
                    LlmFeature.ARTICLE,
                    input.systemPrompt(),
                    outboundPrompt,
                    ARTICLE_TEMPERATURE,
                    LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS,
                    resolveRequestTimeout(input.longForm()),
                    input.longForm() ? LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS : LlmModelConfig.MAX_REQUEST_TIMEOUT_MS,
                    0,
                    null,
                    false,
                    1,
                    0,
                    List.of()
            ));
            return new GenerationCall(
                    new ArticleModelResolver.ModelSelection(routed.platformCode(), routed.modelId(), null),
                    routed.invokeResult()
            );
        } catch (LlmRouteException ex) {
            throw new LlmInvokeException("LLM route failed: " + ex.getMessage(), ex);
        }
    }

    private int resolveRequestTimeout(boolean longForm) {
        return longForm ? ARTICLE_REQUEST_TIMEOUT_MS : LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS;
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
                                List<String> forbiddenPhrases) {
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
}
