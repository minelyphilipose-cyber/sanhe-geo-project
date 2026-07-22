package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ArticleQuestionSceneResolver {

    public static final String GENERAL = "general";
    public static final String SOURCE_REQUEST = "request";
    public static final String SOURCE_CUSTOM_TEMPLATE = "custom_template";
    public static final String SOURCE_GENERAL_FALLBACK = "general_fallback";

    public ArticleQuestionSceneResolution resolve(String requestedSceneCode,
                                                  ArticlePromptTemplate template,
                                                  boolean allowTemplateFallback) {
        String requested = trimToNull(requestedSceneCode);
        if (requested != null) {
            return new ArticleQuestionSceneResolution(requested, requested, SOURCE_REQUEST);
        }
        String templateScene = template == null ? null : trimToNull(template.getQuestionSceneCode());
        if (allowTemplateFallback && templateScene != null) {
            return new ArticleQuestionSceneResolution(null, templateScene, SOURCE_CUSTOM_TEMPLATE);
        }
        return new ArticleQuestionSceneResolution(null, GENERAL, SOURCE_GENERAL_FALLBACK);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
