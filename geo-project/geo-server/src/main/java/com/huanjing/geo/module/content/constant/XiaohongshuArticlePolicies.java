package com.huanjing.geo.module.content.constant;

import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;

public final class XiaohongshuArticlePolicies {

    public static final String NEUTRAL_EDUCATION_TEMPLATE_NAME = "特殊行业小红书中立科普模板";

    private XiaohongshuArticlePolicies() {
    }

    public static boolean isNeutralEducationTemplate(ArticlePromptTemplate template) {
        return template != null && NEUTRAL_EDUCATION_TEMPLATE_NAME.equals(template.getName());
    }
}
