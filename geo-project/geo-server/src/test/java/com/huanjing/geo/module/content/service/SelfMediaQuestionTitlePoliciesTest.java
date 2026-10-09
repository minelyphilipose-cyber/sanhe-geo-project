package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelfMediaQuestionTitlePoliciesTest {
    @Test
    void sharedTitlePolicyOnlyAppliesToSohuAndToutiaoSelfMedia() {
        assertTrue(SelfMediaQuestionTitlePolicies.applies("self_media", "sohu"));
        assertTrue(SelfMediaQuestionTitlePolicies.applies("self_media", "toutiao"));
        for (String platform : List.of("wechat", "douyin", "baijiahao", "zhihu", "xiaohongshu", "netease")) {
            assertFalse(SelfMediaQuestionTitlePolicies.applies("self_media", platform));
        }
        assertFalse(SelfMediaQuestionTitlePolicies.applies("industry_article", "toutiao"));
    }

    @Test
    void subjectMetadataUsesBrandRegionAndConfiguredAliasesWithProjectFallback() {
        Project project = new Project();
        project.setCityName("合肥市");
        project.setBrandName("项目品牌");
        Brand brand = new Brand();
        brand.setCityName(" 阜阳市 ");
        brand.setBrandName(" 测试品牌 ");
        brand.setBrandShortName("测试");

        assertEquals("阜阳市", SelfMediaQuestionTitlePolicies.region(project, brand));
        assertEquals(List.of("测试品牌", "测试", "项目品牌"), SelfMediaQuestionTitlePolicies.brandNames(project, brand));
        brand.setCityName(null);
        assertEquals("合肥市", SelfMediaQuestionTitlePolicies.region(project, brand));
        assertEquals(List.of("项目品牌"), SelfMediaQuestionTitlePolicies.brandNames(project, null));
    }

    @Test
    void missingMetadataDoesNotIntroduceANewToutiaoGenerationFailureOrInventDefaults() {
        String empty = assertDoesNotThrow(() -> SelfMediaQuestionTitlePolicies.titleRequirement(null, null, "toutiao"));
        assertTrue(empty.contains("资料缺失时不得虚构地域或品牌"));
        assertFalse(empty.contains("已提供的主体地域"));
        assertFalse(empty.contains("已提供的品牌名称"));

        Brand brand = new Brand();
        brand.setBrandName("真实品牌");
        String partial = SelfMediaQuestionTitlePolicies.titleRequirement(new Project(), brand, "toutiao");
        assertTrue(partial.contains("已提供的品牌名称：真实品牌"));
        assertFalse(partial.contains("已提供的主体地域"));
    }
}
