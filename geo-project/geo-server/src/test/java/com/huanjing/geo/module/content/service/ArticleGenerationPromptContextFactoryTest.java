package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.KeywordGroup;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleGenerationPromptContextFactoryTest {

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final BrandMapper brandMapper = mock(BrandMapper.class);
    private final KeywordGroupMapper keywordGroupMapper = mock(KeywordGroupMapper.class);
    private final KeywordGroupResultMapper keywordGroupResultMapper = mock(KeywordGroupResultMapper.class);
    private final ProjectKeywordGroupRelMapper projectKeywordGroupRelMapper = mock(ProjectKeywordGroupRelMapper.class);
    private final ArticlePromptTemplateMapper promptTemplateMapper = mock(ArticlePromptTemplateMapper.class);
    private final ArticlePromptTemplateVersionMapper promptTemplateVersionMapper = mock(ArticlePromptTemplateVersionMapper.class);
    private final ArticleDraftMapper articleDraftMapper = mock(ArticleDraftMapper.class);
    private final SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
    private final TemplatePerspectiveService perspectiveService = mock(TemplatePerspectiveService.class);
    private final BrandOfferingPromptSelector offeringPromptSelector = mock(BrandOfferingPromptSelector.class);
    private final MedicalArticleGenerationService medicalArticleGenerationService = mock(MedicalArticleGenerationService.class);

    private ArticleGenerationPromptContextFactory factory;

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticlePromptTemplate.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticlePromptTemplateVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KeywordGroup.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), KeywordGroupResult.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProjectKeywordGroupRel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDictItem.class);
    }

    @BeforeEach
    void setUp() {
        BatchArticlePromptBuilder promptBuilder = new BatchArticlePromptBuilder(
                articleDraftMapper,
                sysDictItemMapper,
                new ObjectMapper(),
                new ArticlePromptVariableRegistry(new ObjectMapper())
        );
        ObjectMapper objectMapper = new ObjectMapper();
        factory = new ArticleGenerationPromptContextFactory(
                projectMapper,
                brandMapper,
                keywordGroupMapper,
                keywordGroupResultMapper,
                projectKeywordGroupRelMapper,
                promptTemplateMapper,
                promptTemplateVersionMapper,
                promptBuilder,
                new ArticlePromptAssemblerV2(
                        objectMapper,
                        new ArticleContentLengthPolicyResolver(),
                        new ArticleEditorialMissionResolver(),
                        new ArticleTemplateCompatibilityResolver()),
                new ArticlePromptContractResolver(objectMapper),
                new ArticleQuestionSceneResolver(),
                new ArticleRuntimePolicyResolver(),
                perspectiveService,
                offeringPromptSelector,
                medicalArticleGenerationService
        );

        when(projectMapper.selectById(10L)).thenReturn(project());
        when(brandMapper.selectById(20L)).thenReturn(brand());
        when(keywordGroupResultMapper.selectList(any())).thenReturn(keywordResults());
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectOne(any())).thenReturn(null);
        when(promptTemplateMapper.selectById(100L)).thenReturn(template());
        when(promptTemplateVersionMapper.selectById(200L)).thenReturn(version());
        when(perspectiveService.resolve(any(), any(), any()))
                .thenReturn(TemplatePerspectiveService.ResolvedPerspective.customer());
        when(offeringPromptSelector.select(any(), any(), any(), any(), any()))
                .thenReturn(new BrandOfferingPromptSelector.SelectionResult(
                        List.<BrandOfferingPromptSelector.SelectedOffering>of()));
        when(medicalArticleGenerationService.resolveContext(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(medicalArticleGenerationService.resolveContextV2(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(medicalArticleGenerationService.applyMedicalPromptV2(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void batchTaskConversionMatchesEquivalentPromptContextRequest() {
        ArticleGenerationPromptContextFactory.PromptContextResult fromBatch =
                factory.buildForBatch(batch(), task());
        ArticleGenerationPromptContextFactory.PromptContextResult fromRequest =
                factory.buildStrict(equivalentRequest());

        assertPromptInputEquivalent(fromBatch.promptInput(), fromRequest.promptInput());
        assertThat(fromBatch.prompt().promptSnapshot()).isEqualTo(fromRequest.prompt().promptSnapshot());
        assertThat(fromBatch.prompt().inputSnapshot()).isEqualTo(fromRequest.prompt().inputSnapshot());
    }

    @Test
    void relatedKeywordsPreferProjectCoreKeywords() {
        Project project = project();
        project.setCoreKeywords("阜阳SPA,养生馆，按摩放松");
        when(projectMapper.selectById(10L)).thenReturn(project);

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildStrict(equivalentRequest());

        assertThat(result.promptInput().relatedKeywords()).containsExactly("阜阳SPA", "养生馆", "按摩放松");
        assertThat(result.prompt().userPrompt()).contains("关键词: 阜阳SPA、养生馆、按摩放松");
    }

    @Test
    void relatedKeywordsFallbackToProjectTierAQuestions() {
        ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
        rel.setProjectId(10L);
        rel.setKeywordGroupId(90L);
        when(projectKeywordGroupRelMapper.selectList(any())).thenReturn(List.of(rel));

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildStrict(equivalentRequest());

        assertThat(result.promptInput().relatedKeywords()).containsExactly("阜阳SPA推荐", "颍州区养生馆");
        assertThat(result.prompt().userPrompt()).contains("关键词: 阜阳SPA推荐、颍州区养生馆");
    }

    @Test
    void batchPromptKeywordsUseProjectFallbackEvenWhenTaskHasKeywordGroup() {
        ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
        rel.setProjectId(10L);
        rel.setKeywordGroupId(90L);
        when(projectKeywordGroupRelMapper.selectList(any())).thenReturn(List.of(rel));

        BatchArticleGenerationBatch batch = batch();
        batch.setTopicSource("keyword_group");
        BatchArticleGenerationTask task = task();
        task.setKeywordGroupId(91L);
        task.setKeywordGroupName("得闲spa_拓词组");

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildForBatch(batch, task);

        assertThat(result.promptInput().topicSource()).isEqualTo("keyword_group");
        assertThat(result.promptInput().keywordGroupId()).isNull();
        assertThat(result.promptInput().keywordGroupName()).isNull();
        assertThat(result.promptInput().relatedKeywords()).containsExactly("阜阳SPA推荐", "颍州区养生馆");
        verify(projectKeywordGroupRelMapper).selectList(any());
    }

    @Test
    void thirdPartyBatchMergesSourceAndSubjectForbiddenPhrases() {
        Project sourceProject = project();
        sourceProject.setExtraForbiddenPhrases("[\"源项目禁词\", \"第一\"]");
        Brand sourceBrand = brand();
        sourceBrand.setForbiddenPhrases("[\"源品牌禁词\", \"最\"]");
        Project subjectProject = project();
        subjectProject.setId(11L);
        subjectProject.setBrandId(21L);
        subjectProject.setExtraForbiddenPhrases("[\"主体项目禁词\"]");
        Brand subjectBrand = brand();
        subjectBrand.setId(21L);
        subjectBrand.setBrandName("主体品牌");
        subjectBrand.setForbiddenPhrases("[\"主体品牌禁词\", \"源品牌禁词\"]");
        when(projectMapper.selectById(10L)).thenReturn(sourceProject);
        when(projectMapper.selectById(11L)).thenReturn(subjectProject);
        when(brandMapper.selectById(20L)).thenReturn(sourceBrand);
        when(brandMapper.selectById(21L)).thenReturn(subjectBrand);

        BatchArticleGenerationTask task = task();
        task.setSubjectProjectId(11L);
        task.setSubjectBrandId(21L);

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildForBatch(batch(), task);

        assertThat(result.forbiddenPhrases())
                .containsExactly("源品牌禁词", "源项目禁词", "主体品牌禁词", "主体项目禁词");
        assertThat(result.promptInput().sourceBrandId()).isEqualTo(20L);
        assertThat(result.promptInput().subjectBrandId()).isEqualTo(21L);
    }

    @Test
    void selfMediaTemplateGenerationDoesNotInjectContentAngle() {
        when(promptTemplateMapper.selectById(101L)).thenReturn(selfMediaTemplate());
        when(promptTemplateVersionMapper.selectById(201L)).thenReturn(selfMediaVersion());

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildStrict(new PromptContextRequest(
                        10L,
                        "manual",
                        "industry_article",
                        "self_media",
                        "baijiahao",
                        "阜阳SPA行业发展趋势",
                        null,
                        "medium",
                        null,
                        null,
                        null,
                        101L,
                        201L,
                        null,
                        null,
                        null,
                        3
                ));

        assertThat(result.promptInput().topicAsQuestion()).isEqualTo("如何理解阜阳SPA行业发展趋势的选择逻辑和常见误区？");
        assertThat(result.prompt().contentAngle()).isNull();
        assertThat(result.prompt().promptSnapshot()).contains("\"contentAngle\":null");
        assertThat(result.prompt().userPrompt()).doesNotContain("本篇从");
        assertThat(result.prompt().userPrompt()).doesNotContain("内容角度");
        assertThat(result.prompt().userPrompt()).doesNotContain("{{contentAngle}}");
    }

    @Test
    void v2TemplateDoesNotExpandMissingTopicQuestionAndUsesRuntimePolicy() {
        ArticlePromptTemplate template = selfMediaTemplate();
        ArticlePromptTemplateVersion version = selfMediaVersion();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(101L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(201L)).thenReturn(version);

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildStrict(new PromptContextRequest(
                        10L, "manual", "industry_article", "self_media", "baijiahao",
                        "阜阳SPA行业发展趋势", null, "medium", null, null, null,
                        101L, 201L, null, null, null, 3
                ));

        assertThat(result.topicAsQuestion()).isNull();
        assertThat(result.promptInput().topicAsQuestion()).isNull();
        assertThat(result.prompt().contentAngle()).isNull();
        assertThat(result.prompt().audiencePerspective()).isNull();
        assertThat(result.prompt().promptSnapshot()).contains("\"promptContract\":\"article_v2\"");
        assertThat(result.runtimePolicy().contactDisclosureMode()).isEqualTo("none");
        assertThat(result.runtimePolicy().allowContactInfo()).isFalse();
        assertThat(result.prompt().userPrompt()).doesNotContain("选择逻辑和常见误区");
        assertThat(result.prompt().userPrompt()).contains("正文不含标题不少于2000字");
        assertThat(result.prompt().promptSnapshot()).contains("\"targetMinChars\":2000");
        assertThat(result.prompt().promptSnapshot()).contains("\"targetMaxChars\":3000");
    }

    @Test
    void v2AutoBatchUsesFrozenRequestedSceneAndDoesNotInheritTemplateScene() {
        ArticlePromptTemplate template = template();
        template.setQuestionSceneCode("brand");
        ArticlePromptTemplateVersion version = version();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(100L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(200L)).thenReturn(version);
        BatchArticleGenerationTask task = task();
        task.setAllocationMode("auto");
        task.setQuestionSceneCode(null);

        ArticleGenerationPromptContextFactory.PromptContextResult result = factory.buildForBatch(batch(), task);

        assertThat(result.promptInput().requestedQuestionSceneCode()).isNull();
        assertThat(result.promptInput().effectiveQuestionSceneCode()).isEqualTo("general");
        assertThat(result.promptInput().questionSceneSource()).isEqualTo("general_fallback");
        assertThat(result.prompt().userPrompt()).contains("最有信息价值的关系")
                .doesNotContain("品牌公开事实与需求之间的关系");
    }

    @Test
    void v2CustomPreviewCanUseTemplateSceneWhenRequestDoesNotProvideOne() {
        ArticlePromptTemplate template = template();
        template.setQuestionSceneCode("brand");
        ArticlePromptTemplateVersion version = version();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(100L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(200L)).thenReturn(version);

        ArticleGenerationPromptContextFactory.PromptContextResult result = factory.buildStrict(equivalentRequest());

        assertThat(result.promptInput().effectiveQuestionSceneCode()).isEqualTo("brand");
        assertThat(result.promptInput().questionSceneSource()).isEqualTo("custom_template");
        assertThat(result.prompt().userPrompt()).contains("品牌公开事实与需求之间的关系");
        assertThat(result.prompt().promptSnapshot()).contains("\"templateCompatibilityLevel\":\"exact\"");
    }

    @Test
    void v2CustomPreviewRejectsExplicitSceneMismatch() {
        ArticlePromptTemplate template = template();
        template.setQuestionSceneCode("brand");
        ArticlePromptTemplateVersion version = version();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(100L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(200L)).thenReturn(version);

        assertThatThrownBy(() -> factory.buildStrict(requestWithScene("qa")))
                .isInstanceOf(com.huanjing.geo.common.exception.BizException.class)
                .hasMessageContaining("question scene");
    }

    @Test
    void v2MedicalContextKeepsUserTopicAndOmitsForbiddenPhraseListFromPrompt() {
        ArticlePromptTemplate template = selfMediaTemplate();
        ArticlePromptTemplateVersion version = selfMediaVersion();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(101L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(201L)).thenReturn(version);
        Brand brand = brand();
        brand.setForbiddenPhrases("[\"一次见效\"]");
        when(brandMapper.selectById(20L)).thenReturn(brand);
        MedicalArticleGenerationService.MedicalPromptContext medicalContext = new MedicalArticleGenerationService.MedicalPromptContext(
                "medical_beauty", "education", null, null, 55L, "吸脂填充风险怎么判断",
                null, "risk", "kernel", 2, false, "style", false,
                null, null, null, null
        );
        when(medicalArticleGenerationService.resolveContextV2(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(medicalContext));

        ArticleGenerationPromptContextFactory.PromptContextResult result = factory.buildStrict(new PromptContextRequest(
                10L, "manual", "industry_article", "self_media", "baijiahao",
                "阜阳祛斑医院推荐", null, "medium", null, null, null,
                101L, 201L, null, null, null,
                "medical_beauty", null, null, null, null, null, 1
        ));

        assertThat(result.v2()).isTrue();
        assertThat(result.promptInput().topic()).isEqualTo("阜阳祛斑医院推荐");
        assertThat(result.promptInput().forbiddenPhrases()).isEmpty();
        assertThat(result.forbiddenPhrases()).containsExactly("一次见效");
        assertThat(result.prompt().userPrompt()).contains("阜阳祛斑医院推荐")
                .doesNotContain("吸脂填充风险怎么判断", "一次见效");
    }

    @Test
    void publishedV2CandidateCanBePreviewedBeforeCurrentVersionSwitch() {
        ArticlePromptTemplate template = selfMediaTemplate();
        template.setCurrentVersionId(999L);
        ArticlePromptTemplateVersion version = selfMediaVersion();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        when(promptTemplateMapper.selectById(101L)).thenReturn(template);
        when(promptTemplateVersionMapper.selectById(201L)).thenReturn(version);

        ArticleGenerationPromptContextFactory.PromptContextResult result =
                factory.buildStrict(new PromptContextRequest(
                        10L, "manual", "industry_article", "self_media", "baijiahao",
                        "阜阳SPA行业发展趋势", null, "medium", null, null, null,
                        101L, 201L, null, null, null, 1
                ));

        assertThat(result.version().getId()).isEqualTo(201L);
        assertThat(result.prompt().promptSnapshot()).contains("\"promptContract\":\"article_v2\"");
    }

    private void assertPromptInputEquivalent(BatchArticlePromptBuilder.PromptBuildInput actual,
                                             BatchArticlePromptBuilder.PromptBuildInput expected) {
        assertThat(actual.project().getId()).isEqualTo(expected.project().getId());
        assertThat(actual.brand().getId()).isEqualTo(expected.brand().getId());
        assertThat(actual.brandStatement()).isEqualTo(expected.brandStatement());
        assertThat(actual.topicSource()).isEqualTo(expected.topicSource());
        assertThat(actual.topic()).isEqualTo(expected.topic());
        assertThat(actual.topicAsQuestion()).isEqualTo(expected.topicAsQuestion());
        assertThat(actual.keywordGroupId()).isEqualTo(expected.keywordGroupId());
        assertThat(actual.keywordGroupName()).isEqualTo(expected.keywordGroupName());
        assertThat(actual.relatedKeywords()).isEqualTo(expected.relatedKeywords());
        assertThat(actual.articleType()).isEqualTo(expected.articleType());
        assertThat(actual.contentStyle()).isEqualTo(expected.contentStyle());
        assertThat(actual.length()).isEqualTo(expected.length());
        assertThat(actual.extraPrompt()).isEqualTo(expected.extraPrompt());
        assertThat(actual.articleIndexInBatch()).isEqualTo(expected.articleIndexInBatch());
        assertThat(actual.forbiddenPhrases()).isEqualTo(expected.forbiddenPhrases());
        assertThat(actual.titleGuide()).isEqualTo(expected.titleGuide());
        assertThat(actual.requestedQuestionSceneCode()).isEqualTo(expected.requestedQuestionSceneCode());
        assertThat(actual.effectiveQuestionSceneCode()).isEqualTo(expected.effectiveQuestionSceneCode());
        assertThat(actual.questionSceneSource()).isEqualTo(expected.questionSceneSource());
    }

    private BatchArticleGenerationBatch batch() {
        BatchArticleGenerationBatch batch = new BatchArticleGenerationBatch();
        batch.setId(30L);
        batch.setProjectId(10L);
        batch.setTopicSource("manual");
        batch.setTopic("批量兜底主题");
        return batch;
    }

    private BatchArticleGenerationTask task() {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setArticleIndexInBatch(1);
        task.setArticleType("stage_advice");
        task.setChannelGroupCode("forum");
        task.setChannelSubCode(null);
        task.setTopic("阜阳哪家SPA馆服务好性价比高");
        task.setTopicAsQuestion("阜阳哪家SPA馆服务好性价比高?");
        task.setLength("medium");
        task.setExtraPrompt("保持真实讨论帖语气");
        task.setPromptTemplateId(100L);
        task.setPromptTemplateVersionId(200L);
        return task;
    }

    private PromptContextRequest equivalentRequest() {
        return new PromptContextRequest(
                10L,
                "manual",
                "stage_advice",
                "forum",
                null,
                "阜阳哪家SPA馆服务好性价比高",
                "阜阳哪家SPA馆服务好性价比高?",
                "medium",
                null,
                null,
                "保持真实讨论帖语气",
                100L,
                200L,
                null,
                null,
                null,
                1
        );
    }

    private PromptContextRequest requestWithScene(String questionSceneCode) {
        return new PromptContextRequest(
                10L, "manual", "stage_advice", "forum", null,
                "阜阳哪家SPA馆服务好性价比高", "阜阳哪家SPA馆服务好性价比高?", "medium",
                null, null, "保持真实讨论帖语气", 100L, 200L,
                null, null, null,
                null, null, null, null, null, null,
                1, questionSceneCode
        );
    }

    private Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setBrandId(20L);
        project.setProjectName("得闲spa项目");
        project.setCompanyName("得闲spa");
        project.setCustomStatement("项目自定义品牌陈述");
        project.setDistrictName("颍州区");
        project.setTargetAudience("本地养生用户");
        return project;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(20L);
        brand.setBrandName("得闲spa");
        brand.setIndustry("美容美业");
        brand.setForbiddenPhrases("[\"第一\"]");
        return brand;
    }

    private List<KeywordGroupResult> keywordResults() {
        KeywordGroupResult first = new KeywordGroupResult();
        first.setId(1L);
        first.setGroupId(90L);
        first.setKeywordText("阜阳SPA推荐");
        KeywordGroupResult second = new KeywordGroupResult();
        second.setId(2L);
        second.setGroupId(90L);
        second.setKeywordText("颍州区养生馆");
        return List.of(first, second);
    }

    private ArticlePromptTemplate template() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(100L);
        template.setName("论坛对比推荐模板");
        template.setChannelGroupCode("forum");
        template.setChannelSubCode(null);
        template.setArticleTypeCode("stage_advice");
        template.setContactDisclosureMode("none");
        template.setStatus(ArticlePromptTemplateService.STATUS_ACTIVE);
        template.setCurrentVersionId(200L);
        template.setUpdatedAt(LocalDateTime.of(2026, 5, 27, 16, 0));
        return template;
    }

    private ArticlePromptTemplateVersion version() {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(200L);
        version.setTemplateId(100L);
        version.setVersionNo(3);
        version.setStatus(ArticlePromptTemplateService.VERSION_PUBLISHED);
        version.setSystemPrompt("系统提示 {{contactBlock}}");
        version.setUserPromptTemplate("""
                主题: {{topic}}
                问题: {{topicAsQuestion}}
                关键词: {{relatedKeywords}}
                标题参考: {{titleGuide}}
                """);
        return version;
    }

    private ArticlePromptTemplate selfMediaTemplate() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(101L);
        template.setName("百家号行业分析模板");
        template.setChannelGroupCode("self_media");
        template.setChannelSubCode("baijiahao");
        template.setArticleTypeCode("industry_article");
        template.setContactDisclosureMode("none");
        template.setStatus(ArticlePromptTemplateService.STATUS_ACTIVE);
        template.setCurrentVersionId(201L);
        template.setUpdatedAt(LocalDateTime.of(2026, 5, 27, 16, 0));
        return template;
    }

    private ArticlePromptTemplateVersion selfMediaVersion() {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(201L);
        version.setTemplateId(101L);
        version.setVersionNo(1);
        version.setStatus(ArticlePromptTemplateService.VERSION_PUBLISHED);
        version.setSystemPrompt("系统提示 {{contentAngle}}");
        version.setUserPromptTemplate("""
                主题: {{topic}}
                问题: {{topicAsQuestion}}
                角度: {{contentAngle}}
                """);
        return version;
    }
}
