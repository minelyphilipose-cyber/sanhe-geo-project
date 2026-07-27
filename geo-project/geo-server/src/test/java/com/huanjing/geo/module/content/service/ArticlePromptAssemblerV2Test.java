package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptAssemblerV2Test {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArticlePromptAssemblerV2 assembler = new ArticlePromptAssemblerV2(
            objectMapper,
            new ArticleContentLengthPolicyResolver(),
            new ArticleEditorialMissionResolver(),
            new ArticleTemplateCompatibilityResolver());

    @Test
    void assemblesSingleGlobalRuleSetAndOmitsEmptyFactsAndStructureAnchors() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        project.setCompanyName("测试科技有限公司");
        project.setCustomStatement("专注企业知识管理服务");

        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        brand.setWebsite("https://example.com");
        brand.setPublicPhone("400-123-4567");
        brand.setPublicAddress("测试路1号");

        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("品牌知识文章");
        template.setDescription("解释用户关心的问题并自然介绍品牌能力");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");

        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, project.getCustomStatement(), "manual", "企业知识库怎么建设", null,
                null, null, List.of("企业知识库", "知识管理"), "industry_article", "authority_media",
                "medium", null, 1, List.of("第一", "最", "最好", "唯一", "专业", "权威", "绝对领先"), null,
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.AUTHORITY_MEDIA, "news_source",
                TemplatePerspectiveCodes.INDUSTRY_NEUTRAL, "brand_only", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertEquals(1, occurrences(result.userPrompt(), "使用自然、清晰、符合现代中文习惯"));
        assertTrue(result.userPrompt().contains("判断最值得讲清楚的核心问题"));
        assertTrue(result.userPrompt().contains("内容顺序服从当前文章的因果关系"));
        assertTrue(result.userPrompt().contains("使用 Markdown 二级标题按语义组织相关段落"));
        assertTrue(result.userPrompt().contains("不规定标题数量、固定名称或固定顺序"));
        assertFalse(result.userPrompt().contains("相似主题"));
        assertTrue(result.userPrompt().contains("可选择的事实素材库，不是必须逐项写入"));
        assertTrue(result.userPrompt().contains("普通主题通常使用1～2项品牌事实"));
        assertFalse(result.userPrompt().contains("未提供"));
        assertFalse(result.userPrompt().contains("结构策略"));
        assertFalse(result.userPrompt().contains("示例标题"));
        assertFalse(result.userPrompt().contains("品牌知识文章"));
        assertFalse(result.userPrompt().contains("解释用户关心的问题并自然介绍品牌能力"));
        assertFalse(result.userPrompt().contains("围绕企业知识库怎么建设完成本次任务"));
        assertFalse(result.userPrompt().contains("400-123-4567"));
        assertFalse(result.userPrompt().contains("测试路1号"));
        assertTrue(result.userPrompt().contains("测试科技有限公司"));
        assertTrue(result.userPrompt().contains("测试品牌"));
        assertTrue(result.userPrompt().contains("https://example.com"));
        assertFalse(result.userPrompt().contains("选择逻辑和常见误区"));
        assertFalse(result.userPrompt().contains("项目禁用表达：第一"));
        assertFalse(result.userPrompt().contains("、最"));
        assertFalse(result.userPrompt().contains("、最好"));
        assertFalse(result.userPrompt().contains("、唯一"));
        assertFalse(result.userPrompt().contains("、专业"));
        assertFalse(result.userPrompt().contains("、权威"));
        assertTrue(result.userPrompt().contains("项目禁用表达：绝对领先"));
        assertNull(result.contentAngle());
        assertNull(result.audiencePerspective());

        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals("article_v2", snapshot.path("promptContract").asText());
        assertEquals("v2_xiaohongshu_special_20260725", snapshot.path("promptRevision").asText());
        assertEquals("brand_only", snapshot.path("runtimePolicy").path("contactDisclosureMode").asText());
        assertEquals(1200, snapshot.path("effectiveLengthPolicy").path("targetMinChars").asInt());
        assertEquals(1800, snapshot.path("effectiveLengthPolicy").path("targetMaxChars").asInt());
        assertEquals(ArticleGenerationTemperatures.V2_STANDARD,
                snapshot.path("effectiveTemperature").asDouble());
        assertTrue(snapshot.path("omittedMaterialKeys").isArray());
    }

    @Test
    void limitsSelfMediaTitleToTwentyEightCharacters() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("抖音图文模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "social_note", "douyin", "short", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "douyin",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("标题不超过28个字"));
        assertTrue(result.userPrompt().contains("正文篇幅较短时以自然分段为主"));
        assertTrue(result.userPrompt().contains("只有确实存在多个相对独立的信息单元时才使用 Markdown 二级标题"));
        assertFalse(result.userPrompt().contains("不得把整篇长文写成从头到尾没有小标题"));
        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals(28, snapshot.path("effectiveTitleMaxChars").asInt());
    }

    @Test
    void specialIndustryDefersQualificationMaterialToMedicalPromptBlock() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试医美");
        brand.setBrandQualificationDescription("医疗机构执业许可");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setArticleTypeCode("cost_analysis");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "医美项目收费通常由哪些部分构成", null,
                null, null, List.of(), "cost_analysis", "toutiao", "medium", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "toutiao",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result =
                assembler.assemble(input, template, version, policy, true);

        assertTrue(result.userPrompt().contains("医美项目收费通常由哪些部分构成"));
        assertTrue(result.userPrompt().contains("涉及成本时说明形成变量"));
        assertFalse(result.userPrompt().contains("资质信息：医疗机构执业许可"));
    }

    @Test
    void specialIndustryXiaohongshuUsesRestrainedInformationNoteDirectionAndKeepsBrandValue() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试医美");
        brand.setMainBusiness("皮肤管理与医疗美容服务");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setArticleTypeCode("social_note");
        template.setQuestionSceneCode("qa");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "医美项目收费通常由哪些部分构成", null,
                null, null, List.of(), "social_note", "xiaohongshu", "short", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "xiaohongshu",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result =
                assembler.assemble(input, template, version, policy, true);

        assertTrue(result.userPrompt().contains("# 小红书特殊行业表达要求"));
        assertTrue(result.userPrompt().contains("小红书特殊行业信息笔记"));
        assertTrue(result.userPrompt().contains("不得改变用户主题"));
        assertTrue(result.userPrompt().contains("正文仍需自然出现品牌名称"));
        assertTrue(result.userPrompt().contains("至少一项与主题直接相关的真实品牌事实"));
        assertTrue(result.userPrompt().contains("通常出现1次，确有解释需要时最多2次"));
        assertTrue(result.userPrompt().contains("不得以“我们机构”“本院”等机构官方身份发声"));
        assertTrue(result.userPrompt().contains("不强制清单格式、固定标题数量、统一开篇或统一总结"));
        assertTrue(result.userPrompt().contains("默认不添加营销型表情、话题标签或联系方式"));
        assertTrue(result.userPrompt().contains("正文不含标题控制在约600～900字"));
        assertTrue(result.userPrompt().contains("测试医美"));
        assertTrue(result.userPrompt().contains("皮肤管理与医疗美容服务"));
        assertFalse(result.userPrompt().contains("每篇必须包含风险"));
    }

    @Test
    void ordinaryXiaohongshuDoesNotReceiveSpecialIndustryDirection() {
        Project project = new Project();
        project.setId(1L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setArticleTypeCode("social_note");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业服务怎么判断", null,
                null, null, List.of(), "social_note", "xiaohongshu", "short", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "xiaohongshu",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result =
                assembler.assemble(input, template, version, policy, false);

        assertTrue(result.userPrompt().contains("小红书信息笔记风格"));
        assertFalse(result.userPrompt().contains("# 小红书特殊行业表达要求"));
    }

    @Test
    void injectsRestrainedZhihuDirectionWithoutRemovingBrandUse() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("知乎问答模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "faq", "zhihu", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "zhihu",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("优先完整回答具体问题"));
        assertTrue(result.userPrompt().contains("品牌可以作为相关选择或文章主体"));
        assertTrue(result.userPrompt().contains("介绍和推荐必须有材料依据并说明限制"));
    }

    @Test
    void strictEditorialBrandTemplateRequiresBrandButRemovesRecommendationAnchors() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        project.setCompanyName("测试科技有限公司");
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        brand.setBrandShortName("测试");
        brand.setMainBusiness("企业知识管理服务");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("今日头条-T3（推荐 · brand · industry_article）");
        template.setDescription("第三方推荐品牌内容");
        template.setQuestionSceneCode("brand");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}明确推荐测试品牌");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "测试品牌适合哪些企业", null,
                null, null, List.of(), "industry_article", "toutiao", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.REVIEW_RECOMMEND, "exact", 20L, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "toutiao",
                TemplatePerspectiveCodes.REVIEW_RECOMMEND, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("每篇必须自然包含品牌信息"));
        assertTrue(result.userPrompt().contains("至少使用一项与主题直接相关的真实品牌事实"));
        assertTrue(result.userPrompt().contains("全文通常出现2～3次且不得超过3次"));
        assertTrue(result.userPrompt().contains("不得把品牌写成默认答案、优先选择或明确推荐结论"));
        assertTrue(result.userPrompt().contains("测试品牌"));
        assertTrue(result.userPrompt().contains("企业知识管理服务"));
        assertFalse(result.userPrompt().contains("可以明确推荐品牌"));
        assertFalse(result.userPrompt().contains("今日头条-T3（推荐"));
        assertFalse(result.userPrompt().contains("围绕测试品牌适合哪些企业明确推荐"));
    }

    @Test
    void strictEditorialGeneralTopicKeepsControlledBrandExposureForBaijiahao() {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        brand.setMainBusiness("企业知识管理服务");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("百家号知识问答模板");
        template.setQuestionSceneCode("qa");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成知识问答");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "faq", "baijiahao", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "baijiahao",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("全文应自然出现1～2次"));
        assertTrue(result.userPrompt().contains("标题默认不出现品牌"));
        assertTrue(result.userPrompt().contains("正文前段、中段或后段"));
        assertTrue(result.userPrompt().contains("结尾只需自然完成文章的主要任务"));
        assertTrue(result.userPrompt().contains("不得把整篇长文写成从头到尾没有小标题的连续正文"));
        assertTrue(result.userPrompt().contains("相近内容归入同一标题"));
        assertTrue(result.userPrompt().contains("列表只用于真正并列的信息"));
        assertFalse(result.userPrompt().contains("标题和开篇不出现品牌"));
        assertFalse(result.userPrompt().contains("先把读者问题与判断依据讲清楚后再带入品牌"));
        assertFalse(result.userPrompt().contains("结尾回到读者关心的问题和判断方法"));
        assertTrue(result.userPrompt().contains("每篇必须自然包含品牌信息"));
        assertTrue(result.userPrompt().contains("减少连续使用“我们”进行自我评价"));
        assertFalse(result.userPrompt().contains("围绕企业知识库怎么建设完成知识问答"));
    }

    @Test
    void recommendationPerspectiveRemainsAvailableOutsideStrictEditorialPlatforms() {
        Project project = new Project();
        project.setId(1L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("公众号推荐模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("围绕{{topic}}完成本次任务");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", null,
                null, null, List.of(), "industry_article", "wechat", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.REVIEW_RECOMMEND, "exact", 20L, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "wechat",
                TemplatePerspectiveCodes.REVIEW_RECOMMEND, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy);

        assertTrue(result.userPrompt().contains("可以明确推荐品牌"));
        assertFalse(result.userPrompt().contains("严格审核平台品牌表达要求"));
    }

    @Test
    void specialIndustryTemplateRemovesConcentratedForbiddenExamples() throws Exception {
        Project project = new Project();
        project.setId(1L);
        Brand brand = new Brand();
        brand.setId(2L);
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("特殊行业科普模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(2);
        version.setUserPromptTemplate("""
                围绕{{topic}}解释选择边界。
                - 不写“种草、无痛、永久、根治、保证、零风险、恢复快”。
                - 重点说明风险、条件和评估依据。
                """);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "阜阳祛斑医院推荐", null,
                null, null, List.of(), "industry_article", "wechat", "medium", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "wechat", TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(input, template, version, policy, true);

        assertFalse(result.userPrompt().contains("种草、无痛、永久"));
        assertFalse(result.userPrompt().contains("避免疗效、安全、时效、持续周期、排名和直接转化类违规承诺"));
        assertFalse(result.userPrompt().contains("重点说明风险、条件和评估依据"));
        assertTrue(result.userPrompt().contains("不得把整篇长文写成从头到尾没有小标题的连续正文"));
        assertFalse(result.userPrompt().contains("小标题必须包含合规"));
        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals(ArticleGenerationTemperatures.DEFAULT,
                snapshot.path("effectiveTemperature").asDouble());
    }

    @Test
    void derivesDifferentContentMissionsFromTemplateMetadataWithoutFixedOutline() {
        Project project = new Project();
        project.setId(1L);
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试品牌");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName("主题适配模板");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setUserPromptTemplate("围绕{{topic}}形成内容");
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业服务如何判断", null,
                null, null, List.of(), "industry_article", "wechat", "long", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "wechat", TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildInput comparisonInput = withTask(input, "comparison", "compare");
        String comparisonPrompt = assembler.assemble(comparisonInput, template, version, policy).userPrompt();
        BatchArticlePromptBuilder.PromptBuildInput costInput = withTask(input, "cost_analysis", "general");
        String costPrompt = assembler.assemble(costInput, template, version, policy).userPrompt();

        assertTrue(comparisonPrompt.contains("真正有意义的差异、判断依据和适用边界"));
        assertTrue(comparisonPrompt.contains("不强制使用表格"));
        assertTrue(costPrompt.contains("形成变量、影响条件和判断边界"));
        assertTrue(costPrompt.contains("不虚构价格或报价"));
        assertFalse(comparisonPrompt.contains("第一段"));
        assertFalse(costPrompt.contains("示范提纲"));
    }

    private int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }

    private BatchArticlePromptBuilder.PromptBuildInput withTask(
            BatchArticlePromptBuilder.PromptBuildInput input,
            String articleType,
            String effectiveScene) {
        return new BatchArticlePromptBuilder.PromptBuildInput(
                input.project(), input.brand(), input.brandStatement(), input.topicSource(), input.topic(),
                input.topicAsQuestion(), input.keywordGroupId(), input.keywordGroupName(), input.relatedKeywords(),
                articleType, input.contentStyle(), input.length(), input.extraPrompt(), input.articleIndexInBatch(),
                input.forbiddenPhrases(), input.titleGuide(), input.perspectiveCode(), input.perspectiveMatchedScope(),
                input.perspectiveMatchedConfigId(), input.selectedOfferings(), input.sourceProjectId(),
                input.sourceBrandId(), input.subjectProjectId(), input.subjectBrandId(),
                effectiveScene, effectiveScene, ArticleQuestionSceneResolver.SOURCE_REQUEST);
    }
}
