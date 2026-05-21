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
            new ObjectMapper()
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

    private BatchArticlePromptBuilder.PromptBuildInput promptInput() {
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
                ""
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
