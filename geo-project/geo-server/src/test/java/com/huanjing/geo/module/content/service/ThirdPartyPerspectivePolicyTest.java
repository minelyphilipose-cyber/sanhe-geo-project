package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThirdPartyPerspectivePolicyTest {

    private final ArticlePromptAssemblerV2 assembler = new ArticlePromptAssemblerV2(
            new ObjectMapper(),
            new ArticleContentLengthPolicyResolver(),
            new ArticleEditorialMissionResolver(),
            new ArticleTemplateCompatibilityResolver()
    );

    @Test
    void industryNeutralDoesNotPromoteWhileReviewRecommendationMayPromote() {
        Project project = new Project();
        Brand brand = new Brand();
        brand.setBrandName("测试品牌");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "智能门锁应该怎么选？", "智能门锁应该怎么选？",
                null, null, List.of(), "industry_article", "wechat", "medium", null,
                1, List.of(), null, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "channel", 1L, List.of()
        );

        String neutralPrompt = assembler.assemble(
                input,
                new ArticlePromptTemplate(),
                new ArticlePromptTemplateVersion(),
                new ArticleRuntimePolicy(ArticlePromptChannels.SELF_MEDIA, "wechat",
                        TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "brand_only", false)
        ).userPrompt();
        String reviewPrompt = assembler.assemble(
                input,
                new ArticlePromptTemplate(),
                new ArticlePromptTemplateVersion(),
                new ArticleRuntimePolicy(ArticlePromptChannels.SELF_MEDIA, "wechat",
                        TemplatePerspectiveCodes.REVIEW_RECOMMEND, "brand_only", false)
        ).userPrompt();

        assertThat(neutralPrompt).contains("不承担品牌推广或推荐任务");
        assertThat(reviewPrompt).contains("可以明确推荐品牌");
    }
}
