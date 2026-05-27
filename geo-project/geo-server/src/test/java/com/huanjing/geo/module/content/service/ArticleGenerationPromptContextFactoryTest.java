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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        factory = new ArticleGenerationPromptContextFactory(
                projectMapper,
                brandMapper,
                keywordGroupMapper,
                keywordGroupResultMapper,
                projectKeywordGroupRelMapper,
                promptTemplateMapper,
                promptTemplateVersionMapper,
                promptBuilder
        );

        when(projectMapper.selectById(10L)).thenReturn(project());
        when(brandMapper.selectById(20L)).thenReturn(brand());
        when(keywordGroupResultMapper.selectList(any())).thenReturn(keywordResults());
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectOne(any())).thenReturn(null);
        when(promptTemplateMapper.selectById(100L)).thenReturn(template());
        when(promptTemplateVersionMapper.selectById(200L)).thenReturn(version());
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
                1
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
}
