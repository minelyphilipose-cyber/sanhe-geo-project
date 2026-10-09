package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.constant.TemplatePerspectiveCodes;
import com.huanjing.geo.module.content.constant.XiaohongshuArticlePolicies;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptAssemblerV2Test {
    @ParameterizedTest
    @ValueSource(strings = {TemplatePerspectiveCodes.CUSTOMER, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
            TemplatePerspectiveCodes.REVIEW_RECOMMEND})
    void toutiaoUsesRegionalBrandQuestionsWithoutChangingBodyOrContactPolicies(String perspective) throws Exception {
        Project project = new Project();
        project.setCityName("合肥市");
        Brand brand = new Brand();
        brand.setBrandName("测试品牌");
        brand.setBrandShortName("测试");
        brand.setCityName("阜阳市");
        brand.setMainBusiness("企业知识管理服务");
        var input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "企业知识库怎么建设", "企业知识库怎么建设",
                null, null, List.of(), "faq", "toutiao", "medium", null,
                1, List.of(), null, perspective, "default", null, List.of());
        var policy = new ArticleRuntimePolicyResolver().resolve(null, "self_media", "toutiao", perspective);
        var result = assembler.assemble(input, new ArticlePromptTemplate(), new ArticlePromptTemplateVersion(), policy, false);

        assertTrue(result.userPrompt().contains("今日头条标题要求"));
        assertTrue(result.userPrompt().contains("主体地域“阜阳市”"));
        assertTrue(result.userPrompt().contains("品牌名称“测试品牌”"));
        assertTrue(result.userPrompt().contains("疑问句或反问句"));
        assertTrue(result.userPrompt().contains("把品牌评价留给正文依据事实回答"));
        assertTrue(result.userPrompt().contains("追问与 A 级选题相关"));
        assertTrue(result.userPrompt().contains("28字上限"));
        assertFalse(result.userPrompt().contains("72字上限"));
        assertFalse(result.userPrompt().contains("标题默认不出现品牌"));
        assertFalse(result.userPrompt().contains("标题只有在用户主题本身明确围绕该品牌时"));
        assertFalse(result.userPrompt().contains("搜狐正文要求"));
        assertFalse(result.userPrompt().contains("最多仅可展示已配置的公开地址"));
        if (TemplatePerspectiveCodes.CUSTOMER.equals(perspective)) {
            assertTrue(result.userPrompt().contains("全文应自然出现1～2次"));
            assertTrue(result.userPrompt().contains("至少使用一项与主题直接相关的真实品牌事实"));
        }
        assertEquals(policy.contactDisclosureMode(),
                new ObjectMapper().readTree(result.inputSnapshot()).path("runtimePolicy").path("contactDisclosureMode").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {TemplatePerspectiveCodes.CUSTOMER, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
            TemplatePerspectiveCodes.REVIEW_RECOMMEND})
    void sohuAnswersRegionalBrandTitleAndLimitsContactsToAddress(String perspective) {
        Project project = new Project();
        project.setCityName("合肥市");
        Brand brand = new Brand();
        brand.setBrandName("华为智家");
        brand.setCityName("阜阳市");
        brand.setMainBusiness("全屋智能设计与服务");
        brand.setPublicAddress("阜阳市测试路88号");
        brand.setPublicPhone("13812345678");
        brand.setWebsite("https://brand.example.com");
        var input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "全屋智能需要提前规划吗", "全屋智能需要提前规划吗",
                null, null, List.of("全屋智能"), "industry_article", "sohu", "medium", null,
                1, List.of(), null, perspective, "default", null, List.of());
        var policy = new ArticleRuntimePolicyResolver().resolve(null, "self_media", "sohu", perspective);
        var result = assembler.assemble(input, new ArticlePromptTemplate(), new ArticlePromptTemplateVersion(), policy, false);
        assertTrue(result.userPrompt().contains("主体地域“阜阳市”"));
        assertTrue(result.userPrompt().contains("品牌名称“华为智家”"));
        assertTrue(result.userPrompt().contains("疑问句或反问句"));
        assertTrue(result.userPrompt().contains("一句或两句均可"));
        assertTrue(result.userPrompt().contains("把品牌评价留给正文依据事实回答"));
        assertTrue(result.userPrompt().contains("先提地域行业问题，再接地域品牌追问"));
        assertTrue(result.userPrompt().contains("直接提出品牌的具体问题，再接相关追问"));
        assertTrue(result.userPrompt().contains("追问与 A 级选题相关"));
        assertTrue(result.userPrompt().contains("紧扣其核心关切"));
        assertTrue(result.userPrompt().contains("不把不同问题都转向同一个产品或服务维度"));
        assertTrue(result.userPrompt().contains("说明式导语替代品牌追问"));
        assertTrue(result.userPrompt().contains("完整回答或解释最终标题"));
        assertTrue(result.userPrompt().contains("细致说明与主题相关"));
        assertFalse(result.userPrompt().contains("标题默认不出现品牌"));
        assertFalse(result.userPrompt().contains("标题只有在用户主题本身明确围绕该品牌时"));
        assertFalse(result.userPrompt().contains("全文应自然出现1～2次"));
        assertFalse(result.userPrompt().contains("品牌只可在确有解释价值时作为普通样本出现"));
        assertTrue(result.userPrompt().contains("最多仅可展示已配置的公开地址"));
        assertTrue(result.userPrompt().contains("阜阳市测试路88号"));
        assertTrue(result.userPrompt().contains("全屋智能设计与服务"));
        assertTrue(result.userPrompt().contains("2000～3000字"));
        assertFalse(result.userPrompt().contains("13812345678"));
        assertFalse(result.userPrompt().contains("brand.example.com"));
        assertFalse(result.userPrompt().contains("可以明确推荐品牌"));
        assertFalse(result.userPrompt().contains("只可在确有解释价值时作为普通样本"));
        brand.setPublicAddress(null);
        assertTrue(assembler.assemble(input, new ArticlePromptTemplate(), new ArticlePromptTemplateVersion(), policy, false)
                .userPrompt().contains("当前未配置公开地址，省略地址"));
    }
    @ParameterizedTest
    @ValueSource(strings = {TemplatePerspectiveCodes.CUSTOMER, TemplatePerspectiveCodes.INDUSTRY_NEUTRAL,
            TemplatePerspectiveCodes.REVIEW_RECOMMEND})
    void medicalBeautyDouyinAnswersTitleWithRelevantBrandFactsOnlyInItsScope(String perspective) throws Exception {
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("阜阳永功医疗美容");
        brand.setComplianceIndustryCode("medical_beauty");
        brand.setCoreProducts("品牌核心服务资料");
        brand.setBusinessIntro("品牌业务介绍资料");
        brand.setBrandPositioning("旧宣传定位");
        brand.setBrandCaseDescription("笼统口碑宣传");
        var offering = new BrandOfferingPromptSelector.SelectedOffering(
                12L, "皮肤管理服务", List.of(), null, "关注皮肤管理流程", "先记录关注问题，再说明服务流程", null);
        var input = new BatchArticlePromptBuilder.PromptBuildInput(
                new Project(), brand, null, "manual", "阜阳哪家医美门诊部服务比较细致", null,
                null, null, List.of(), "social_note", "douyin", "short", null,
                1, List.of(), null, perspective, "default", null, List.of(offering));
        var template = new ArticlePromptTemplate();
        var version = new ArticlePromptTemplateVersion();
        var policy = new ArticleRuntimePolicy(ArticlePromptChannels.SELF_MEDIA, "douyin",
                perspective, "none", false);

        var result = assembler.assemble(input, template, version, policy, true);

        assertTrue(result.userPrompt().contains("正文必须自然提及本篇真实品牌名称"));
        assertTrue(result.userPrompt().contains("不限定为一次或一句"));
        assertTrue(result.userPrompt().contains("不使用“以某品牌为例、比如某品牌、拿某品牌举例、作为行业样本”等举例式引入"));
        assertTrue(result.userPrompt().contains("正文以完整回答或解释最终标题为首要任务"));
        assertTrue(result.userPrompt().contains("这是写作思路，不要求先写科普、再另起品牌段"));
        assertTrue(result.userPrompt().contains("产品或服务资料只是可选的相关依据，不是必写清单"));
        assertTrue(result.userPrompt().contains("不因已传入产品资料就强制介绍"));
        assertTrue(result.userPrompt().contains("不为植入品牌改换标题的关注点"));
        assertFalse(result.userPrompt().contains("将该品牌作为实际介绍对象"));
        assertFalse(result.userPrompt().contains("品牌信息介绍视角"));
        assertFalse(result.userPrompt().contains("正文应具体介绍至少一项"));
        assertTrue(result.userPrompt().contains("产品或服务1：名称：皮肤管理服务"));
        assertTrue(result.userPrompt().contains("先记录关注问题，再说明服务流程"));
        assertFalse(result.userPrompt().contains("品牌核心服务资料"));
        assertFalse(result.userPrompt().contains("品牌业务介绍资料"));
        assertTrue(result.userPrompt().contains("可以有一点调皮"));
        assertTrue(result.userPrompt().contains("独立的 Markdown 一级标题"));
        assertFalse(result.userPrompt().contains("旧宣传定位"));
        assertFalse(result.userPrompt().contains("笼统口碑宣传"));
        assertFalse(result.userPrompt().contains("# 全局写作原则"));
        assertTrue(objectMapper.readTree(result.promptSnapshot()).path("omittedMaterialKeys").toString()
                .contains("brandCaseDescription"));
        assertEquals("皮肤管理服务", objectMapper.readTree(result.inputSnapshot())
                .path("selectedOfferings").get(0).path("name").asText());
        assertFalse(result.userPrompt().contains("通常出现一次，用一句话结合一项"));
        assertFalse(result.userPrompt().contains("品牌仅作为真实主体背景"));
        assertFalse(result.userPrompt().contains("只能作为普通行业样本出现"));
        assertTrue(result.userPrompt().contains("标题转换为同一关注点的知识问题"));
        assertTrue(result.userPrompt().contains("阜阳永功医疗美容"));
        assertFalse(result.userPrompt().contains("营销信息必须参与问题解释"));
        assertFalse(result.userPrompt().contains("采用客户官方身份表达"));
        assertTrue(result.userPrompt().contains("每段补充原因、区别、可核对的信息或具体问题"));
        assertTrue(result.userPrompt().contains("不再重复一遍观点或口号"));
        assertTrue(result.userPrompt().contains("正文是否真正回答它"));
        assertTrue(result.userPrompt().contains("不硬接无关项目"));
        assertTrue(result.userPrompt().contains("不给个体诊疗建议"));
        assertTrue(result.userPrompt().contains("不确定时收窄表述"));
        assertTrue(result.userPrompt().contains("正文不含标题控制在约600～900字"));
        assertTrue(result.userPrompt().contains("让读者知道这条信息跟自己的问题有什么关系"));
        var lengthPolicy = objectMapper.readTree(result.promptSnapshot()).path("effectiveLengthPolicy");
        assertEquals(600, lengthPolicy.path("targetMinChars").asInt());
        assertEquals(900, lengthPolicy.path("targetMaxChars").asInt());
        assertEquals("v2_douyin_medical_beauty_conversation_20260917",
                objectMapper.readTree(result.promptSnapshot()).path("promptRevision").asText());

        brand.setComplianceIndustryCode("oral");
        assertFalse(assembler.assemble(input, template, version, policy, true).userPrompt()
                .contains("医美抖音图文的专用内容任务"));
        brand.setComplianceIndustryCode("medical_beauty");
        var otherChannel = new ArticleRuntimePolicy(ArticlePromptChannels.SELF_MEDIA, "wechat",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);
        assertFalse(assembler.assemble(input, template, version, otherChannel, true).userPrompt()
                .contains("医美抖音图文的专用内容任务"));
    }


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
        assertEquals("v2_xiaohongshu_converged_20260804", snapshot.path("promptRevision").asText());
        assertEquals("brand_only", snapshot.path("runtimePolicy").path("contactDisclosureMode").asText());
        assertEquals(1200, snapshot.path("effectiveLengthPolicy").path("targetMinChars").asInt());
        assertEquals(1800, snapshot.path("effectiveLengthPolicy").path("targetMaxChars").asInt());
        assertEquals(ArticleGenerationTemperatures.V2_STANDARD,
                snapshot.path("effectiveTemperature").asDouble());
        assertTrue(snapshot.path("omittedMaterialKeys").isArray());
    }

    @Test
    void limitsDouyinTitleToTwentyCharacters() throws Exception {
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

        assertTrue(result.userPrompt().contains("独立的 Markdown 一级标题（# 标题），不超过20字"));
        assertTrue(result.userPrompt().contains("正文篇幅较短时以自然分段为主"));
        assertTrue(result.userPrompt().contains("只有确实存在多个相对独立的信息单元时才使用 Markdown 二级标题"));
        assertFalse(result.userPrompt().contains("不得把整篇长文写成从头到尾没有小标题"));
        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals(20, snapshot.path("effectiveTitleMaxChars").asInt());
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
    void neutralEducationTemplateOmitsBrandFactsAndUsesTightOutputRules() throws Exception {
        Project project = new Project();
        project.setId(1L);
        project.setBrandId(2L);
        project.setCompanyName("测试医疗有限公司");
        Brand brand = new Brand();
        brand.setId(2L);
        brand.setBrandName("测试医美");
        brand.setMainBusiness("医疗美容服务");
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(10L);
        template.setName(XiaohongshuArticlePolicies.NEUTRAL_EDUCATION_TEMPLATE_NAME);
        template.setArticleTypeCode("social_note");
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(11L);
        version.setVersionNo(1);
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "皮肤屏障为什么会随环境变化", null,
                null, null, List.of(), "social_note", "xiaohongshu", "short", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "xiaohongshu",
                TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result =
                assembler.assemble(input, template, version, policy, true);

        assertTrue(result.userPrompt().contains("特殊行业中立科普"));
        assertTrue(result.userPrompt().contains("不承担品牌露出"));
        assertTrue(result.userPrompt().contains("正文不含标题控制在约500～700字"));
        assertTrue(result.userPrompt().contains("标题不超过20个字"));
        assertTrue(result.userPrompt().contains("本模板不向正文提供企业、品牌、具体机构、品牌专属产品服务、资质或案例素材"));
        assertFalse(result.userPrompt().contains("测试医美"));
        assertFalse(result.userPrompt().contains("医疗美容服务"));

        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals("neutral_education", snapshot.path("xiaohongshuContentMode").asText());
        assertEquals(500, snapshot.path("effectiveLengthPolicy").path("targetMinChars").asInt());
        assertEquals(700, snapshot.path("effectiveLengthPolicy").path("targetMaxChars").asInt());
        assertEquals(20, snapshot.path("effectiveTitleMaxChars").asInt());
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

    @Test
    void addsCompleteDouyinRulesAndAtMostFivePublishedTitles() throws Exception {
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
        BatchArticlePromptBuilder.PromptBuildInput input = new BatchArticlePromptBuilder.PromptBuildInput(
                project, brand, null, "manual", "装修前要不要预留全屋智能", null,
                null, null, List.of("全屋智能"), "social_note", "douyin", "medium", null,
                1, List.of(), null, TemplatePerspectiveCodes.CUSTOMER, "default", null, List.of()
        );
        ArticleRuntimePolicy policy = new ArticleRuntimePolicy(
                ArticlePromptChannels.SELF_MEDIA, "douyin", TemplatePerspectiveCodes.CUSTOMER, "none", false);

        BatchArticlePromptBuilder.PromptBuildResult result = assembler.assemble(
                input, template, version, policy, false,
                List.of("标题一", "标题二", "标题三", "标题四", "标题五", "标题六"));

        assertTrue(result.userPrompt().contains("# 抖音图文自然行文规则"));
        assertTrue(result.userPrompt().contains("不要按照“项目是什么—设备是什么—优势是什么”的说明书逻辑机械罗列"));
        assertTrue(result.userPrompt().contains("不要把小节写成彼此独立的知识卡片"));
        assertTrue(result.userPrompt().contains("这些标题只用于了解近期发布语境"));
        assertTrue(result.userPrompt().contains("- 标题五"));
        assertFalse(result.userPrompt().contains("标题六"));
        JsonNode snapshot = objectMapper.readTree(result.promptSnapshot());
        assertEquals(5, snapshot.path("recentTitles").size());
        assertEquals("v2_douyin_natural_20260902", snapshot.path("promptRevision").asText());
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
