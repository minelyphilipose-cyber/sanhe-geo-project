package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SohuArticlePoliciesTest {
    @Test
    void validatesRegionalBrandTitlesWithoutRequiringOneQuestionPattern() {
        Project project = new Project();
        project.setCityName("合肥市");
        Brand brand = new Brand();
        brand.setBrandName("华为智家");
        brand.setBrandShortName("智家");
        brand.setCityName("阜阳市");
        assertDoesNotThrow(() -> SohuArticlePolicies.validateTitle("阜阳华为智家，全屋智能需要提前规划吗？", project, brand));
        assertDoesNotThrow(() -> SohuArticlePolicies.validateTitle("阜阳智家如何规划照明？不同空间不需要区别吗？", project, brand));
        assertDoesNotThrow(() -> SohuArticlePolicies.validateTitle("阜阳全屋智能有线与无线方案怎么选？华为智家服务怎么样？", project, brand));
        assertDoesNotThrow(() -> SohuArticlePolicies.validateTitle("阜阳华为智家的有线与无线方案怎么选？服务怎么样？", project, brand));
        assertDoesNotThrow(() -> SohuArticlePolicies.validateTitle("阜阳华为智家介绍全屋智能预留方案", project, brand));
        assertThrows(BizException.class, () -> SohuArticlePolicies.validateTitle("华为智家如何规划照明？", project, brand));
        assertThrows(BizException.class, () -> SohuArticlePolicies.validateTitle("合肥华为智家如何规划照明？", project, brand));
        assertThrows(BizException.class, () -> SohuArticlePolicies.validateTitle("阜阳照明需要提前规划吗？", project, brand));
        assertThrows(BizException.class, () -> SohuArticlePolicies.validateTitle("阜阳华为智家" + "细致规划".repeat(20), project, brand));
        assertThrows(BizException.class, () -> SohuArticlePolicies.titleRequirement(new Project(), new Brand()));
        assertEquals(72, ArticlePromptChannels.maxTitleChars("self_media", "sohu"));
        assertEquals(28, ArticlePromptChannels.maxTitleChars("self_media", "toutiao"));
    }
}
