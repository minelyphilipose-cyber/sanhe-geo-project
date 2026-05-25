package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmInvokeException;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmInvoker;
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

    private final LlmInvoker llmInvoker;
    private final ArticleModelResolver modelResolver;
    private final MarkdownImageReferenceValidator markdownImageReferenceValidator;
    private final ArticleAiDraftPromptFilter promptFilter;
    private final BatchArticleQualityChecker qualityChecker;

    public GeneratedArticle generate(GenerateInput input) throws LlmInvokeException {
        ArticleModelResolver.ModelSelection model = modelResolver.resolve(
                input.modelPlatformCode(),
                input.modelId(),
                input.systemPrompt(),
                input.longForm()
        );
        String outboundPrompt = promptFilter.filterOutboundPrompt(
                input.userPrompt(),
                input.project(),
                input.brand(),
                input.allowContactInfo()
        );
        LlmInvokeResult result = llmInvoker.invoke(outboundPrompt, model.config());
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
}
