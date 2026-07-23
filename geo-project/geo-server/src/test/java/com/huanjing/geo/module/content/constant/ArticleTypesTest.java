package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTypesTest {

    @Test
    void exposesCanonicalLabelsForEverySupportedType() {
        assertEquals("通用文章", ArticleTypes.label(ArticleTypes.GENERAL_ARTICLE));
        assertEquals("问答文章", ArticleTypes.label(ArticleTypes.FAQ));
        assertEquals("行业文章", ArticleTypes.label(ArticleTypes.INDUSTRY_ARTICLE));
        assertEquals(12, ArticleTypes.LABELS.size());
        assertTrue(ArticleTypes.LABELS.keySet().stream().allMatch(ArticleTypes::isSupported));
    }

    @Test
    void keepsNeutralManualTypeOutOfGeneratedTemplateTypes() {
        assertTrue(ArticleTypes.isSupported(ArticleTypes.GENERAL_ARTICLE));
        assertFalse(ArticleTypes.isGeneratedContentType(ArticleTypes.GENERAL_ARTICLE));
        assertTrue(ArticleTypes.isGeneratedContentType(ArticleTypes.BUYING_GUIDE));
    }
}
