package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchArticleQualityCheckerV2Test {

    private final BatchArticleQualityChecker checker = new BatchArticleQualityChecker(new ObjectMapper());

    @Test
    void doesNotRejectBrandFrequencyClichesOrEmoji() {
        Brand brand = new Brand();
        brand.setBrandName("测试品牌");
        String content = "# 标题\n随着需求变化，测试品牌提供信息。测试品牌说明条件。测试品牌回应问题。🙂";

        BatchArticleQualityChecker.QualityResult result = checker.check(content, brand, List.of());

        assertFalse(result.rewriteRequired());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void treatsUnresolvedVariablesAndForbiddenPhrasesAsHardErrors() {
        BatchArticleQualityChecker.QualityResult result = checker.check(
                "# 标题\n{{brandName}} 是绝对领先的选择。", null, List.of("绝对领先"));

        assertTrue(result.rewriteRequired());
        assertTrue(result.issues().stream().anyMatch(issue -> "unresolved_variable".equals(issue.type())));
        assertTrue(result.issues().stream().anyMatch(issue -> "forbidden_phrase".equals(issue.type())));
    }
}
