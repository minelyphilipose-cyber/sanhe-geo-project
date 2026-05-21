package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTypesTest {

    @Test
    void supportedTypesAreAlignedWithArticleTypeLabels() {
        List<String> labelCodes = ArticlePromptChannels.ARTICLE_TYPE_LABELS.keySet().stream().toList();

        labelCodes.forEach(code -> assertTrue(ArticleTypes.isSupported(code), code + " should be supported"));
        assertFalse(ArticleTypes.isSupported("unsupported_type"));
    }
}
