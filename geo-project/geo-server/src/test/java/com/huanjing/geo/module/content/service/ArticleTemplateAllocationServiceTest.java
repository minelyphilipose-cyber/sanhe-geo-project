package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleTemplateAllocationServiceTest {

    private ArticlePromptTemplateMapper templateMapper;
    private ArticlePromptTemplateVersionMapper versionMapper;
    private ArticleTemplateAllocationService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ArticlePromptTemplateMapper.class);
        versionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        service = new ArticleTemplateAllocationService(templateMapper, versionMapper, new QuestionScenePlatformSuggestionService());
    }

    @Test
    void allocateUsesMatchingQuestionSceneBeforeGenericTemplates() {
        ArticlePromptTemplate brand = template(1L, "brand", 10);
        ArticlePromptTemplate generic = template(2L, null, 10);
        when(templateMapper.selectList(any())).thenReturn(List.of(brand, generic));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, 1L));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, 2L));

        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated =
                service.allocate("forum", null, "brand", 3);

        assertEquals(1, allocated.size());
        assertEquals(1L, allocated.get(0).template().getId());
        assertEquals(3, allocated.get(0).count());
    }

    @Test
    void allocateFallsBackToGenericWhenSceneTemplateIsMissing() {
        ArticlePromptTemplate deal = template(1L, "deal", 10);
        ArticlePromptTemplate generic = template(2L, null, 10);
        when(templateMapper.selectList(any())).thenReturn(List.of(deal, generic));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, 1L));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, 2L));

        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated =
                service.allocate("forum", null, "brand", 2);

        assertEquals(1, allocated.size());
        assertEquals(2L, allocated.get(0).template().getId());
        assertEquals(2, allocated.get(0).count());
    }

    private ArticlePromptTemplate template(Long id, String questionSceneCode, int weight) {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(id);
        template.setName("template-" + id);
        template.setChannelGroupCode("forum");
        template.setArticleTypeCode("faq");
        template.setQuestionSceneCode(questionSceneCode);
        template.setWeight(weight);
        template.setStatus(ArticlePromptTemplateService.STATUS_ACTIVE);
        template.setCurrentVersionId(100L + id);
        return template;
    }

    private ArticlePromptTemplateVersion version(Long id, Long templateId) {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(id);
        version.setTemplateId(templateId);
        version.setStatus(ArticlePromptTemplateService.VERSION_PUBLISHED);
        return version;
    }
}
