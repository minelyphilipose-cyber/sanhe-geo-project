package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleTitleDuplicateCheckerTest {

    @Test
    void detectsExactTitleAfterUnicodeWhitespaceAndPunctuationNormalization() {
        ArticleDraftMapper mapper = mock(ArticleDraftMapper.class);
        ArticleDraft existing = new ArticleDraft();
        existing.setTitle("企业知识库，应该怎么建设？");
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        ArticleTitleDuplicateChecker checker = new ArticleTitleDuplicateChecker(mapper);

        assertTrue(checker.exists(1L, "企业知识库应该怎么建设"));
        assertFalse(checker.exists(1L, "企业知识库建设需要哪些材料"));
    }
}
