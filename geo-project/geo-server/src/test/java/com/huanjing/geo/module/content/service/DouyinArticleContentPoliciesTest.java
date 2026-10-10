package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DouyinArticleContentPoliciesTest {
    @ParameterizedTest
    @CsvSource({"699,false", "700,true", "900,true", "901,false"})
    void enforcesBodyLengthWithoutCountingTitle(int length, boolean valid) {
        String article = "# 抖音标题\n\n" + "文".repeat(length);
        assertEquals(length, DouyinArticleContentPolicies.bodyLength(article));
        assertEquals(valid, DouyinArticleContentPolicies.lengthIssue(article) == null);
    }

    @Test
    void excludesMarkdownImagesWhitespaceAndLinkDestinationsButCountsVisibleText() {
        String article = "# 标题\n\n## 资质\n- **服务**：60分钟\n\n"
                + "![图](https://example.com/very-long-image-link.png)\n"
                + "[说明](https://example.com/very-long-reference)\n"
                + " 😀";
        assertEquals("资质服务：60分钟说明😀".codePointCount(0, "资质服务：60分钟说明😀".length()),
                DouyinArticleContentPolicies.bodyLength(article));
    }
}
