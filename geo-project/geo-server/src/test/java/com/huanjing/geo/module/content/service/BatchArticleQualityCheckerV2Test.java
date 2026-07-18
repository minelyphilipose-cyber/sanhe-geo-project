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

    @Test
    void ignoresOverbroadStandaloneTermsButKeepsCompletePhrasesEnforceable() {
        BatchArticleQualityChecker.QualityResult normalProse = checker.check(
                "# 标题\n第一，最好先参考权威资料，由专业人员判断；价格不是唯一标准，最后再核对安全边界。",
                null,
                List.of("第一", "最", "最好", "权威", "专业", "唯一", "安全", "推荐"));
        BatchArticleQualityChecker.QualityResult exaggeratedClaim = checker.check(
                "# 标题\n该品牌被称为行业第一。", null, List.of("行业第一"));

        assertFalse(normalProse.rewriteRequired());
        assertTrue(normalProse.issues().isEmpty());
        assertTrue(exaggeratedClaim.rewriteRequired());
        assertTrue(exaggeratedClaim.issues().stream().anyMatch(issue -> "forbidden_phrase".equals(issue.type())));
    }
}
