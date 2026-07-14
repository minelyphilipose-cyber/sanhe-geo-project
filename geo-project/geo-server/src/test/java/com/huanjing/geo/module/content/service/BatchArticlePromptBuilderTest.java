package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchArticlePromptBuilderTest {

    private final ArticleDraftMapper articleDraftMapper = mock(ArticleDraftMapper.class);
    private final SysDictItemMapper sysDictItemMapper = mock(SysDictItemMapper.class);
    private final BatchArticlePromptBuilder builder = new BatchArticlePromptBuilder(
            articleDraftMapper,
            sysDictItemMapper,
            new ObjectMapper(),
            new ArticlePromptVariableRegistry(new ObjectMapper())
    );

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ArticleDraft.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDictItem.class);
    }

    @Test
    void buildFromTemplateUsesIndustryDictValueInsteadOfInternalKey() {
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());
        SysDictItem dictItem = new SysDictItem();
        dictItem.setDictKey("beauty_cosmetic");
        dictItem.setDictValue("美容美业");
        dictItem.setEnabled(true);
        when(sysDictItemMapper.selectOne(any())).thenReturn(dictItem);

        BatchArticlePromptBuilder.PromptBuildResult result = builder.buildFromTemplate(
                promptInput(),
                template(),
                version()
        );

        assertThat(result.userPrompt())
                .contains("行业：美容美业")
                .contains("品类：美容美业")
                .contains("适配客群：本地养生用户")
                .doesNotContain("beauty_cosmetic");
    }

    @Test
    void buildFromTemplateIncludesOnlyNonBlankOfferingFields() {
        when(articleDraftMapper.selectList(any())).thenReturn(List.of());
        when(sysDictItemMapper.selectOne(any())).thenReturn(null);
        BrandOfferingPromptSelector.SelectedOffering offering = new BrandOfferingPromptSelector.SelectedOffering(
                101L,
                " 舒缓芳疗 ",
                List.of("芳疗 SPA", " "),
                null,
                " 久坐放松 ",
                " ",
                null
        );

        BatchArticlePromptBuilder.PromptBuildResult result = builder.buildFromTemplate(
                promptInput(List.of(offering)),
                template(),
                version()
        );

        assertThat(result.userPrompt())
                .contains("本篇可引用的产品/服务项目/特色业务项")
                .contains("- 舒缓芳疗（简称：芳疗 SPA）")
                .contains("适用场景：久坐放松")
                .doesNotContain("目标人群：")
                .doesNotContain("介绍：")
                .doesNotContain("资质描述：");
    }

    @Test
    void contactBlockUsesRequestedDisclosureMode() {
        Brand brand = new Brand();
        brand.setWebsite("https://example.com");
        brand.setPublicPhone("400-800-1234");
        brand.setPublicAddress("上海市测试路 1 号");

        assertThat(builder.buildContactBlock(brand, "full"))
                .contains("访问官网 https://example.com")
                .contains("致电 400-800-1234 咨询")
                .contains("地址:上海市测试路 1 号");
        assertThat(builder.buildContactBlock(brand, "none")).isEmpty();
        assertThat(builder.buildContactBlock(brand, "soft_hint")).isEqualTo("感兴趣的可以自己搜一下相关信息了解。");
    }

    private BatchArticlePromptBuilder.PromptBuildInput promptInput() {
        return promptInput(List.of());
    }

    private BatchArticlePromptBuilder.PromptBuildInput promptInput(
            List<BrandOfferingPromptSelector.SelectedOffering> selectedOfferings) {
        Project project = new Project();
        project.setId(9L);
        project.setBrandId(8L);
        project.setProjectName("得闲spa");
        project.setCompanyName("得闲spa");
        project.setTargetAudience("本地养生用户");

        Brand brand = new Brand();
        brand.setBrandName("阜阳市颍州区得闲养生馆");
        brand.setBrandShortName("得闲spa");
        brand.setIndustry("beauty_cosmetic");
        brand.setCoreProducts("芳疗身体SPA,面部抗衰");

        return new BatchArticlePromptBuilder.PromptBuildInput(
                project,
                brand,
                "核心产品：芳疗身体SPA、面部抗衰",
                "manual",
                "阜阳哪家SPA馆服务好性价比高",
                "阜阳哪家SPA馆服务好性价比高?",
                null,
                null,
                List.of(),
                "stage_advice",
                "forum",
                "medium",
                null,
                0,
                List.of(),
                "",
                "customer",
                TemplatePerspectiveService.MATCH_SCOPE_DEFAULT,
                null,
                selectedOfferings
        );
    }

    private ArticlePromptTemplate template() {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(23L);
        template.setName("论坛推荐理由答疑模板");
        template.setChannelGroupCode("forum");
        template.setArticleTypeCode("stage_advice");
        template.setContactDisclosureMode("none");
        return template;
    }

    private ArticlePromptTemplateVersion version() {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(38L);
        version.setTemplateId(23L);
        version.setVersionNo(1);
        version.setSystemPrompt("系统提示");
        version.setUserPromptTemplate("【可用品牌事实】\n- 行业：{{industry}}\n- 品类：{{category}}\n- 品牌：{{brandName}}\n- 适配客群：{{targetAudience}}\n");
        return version;
    }
}
