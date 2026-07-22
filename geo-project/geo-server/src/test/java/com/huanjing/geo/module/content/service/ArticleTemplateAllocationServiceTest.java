package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleTemplateAllocationServiceTest {

    private ArticlePromptTemplateMapper templateMapper;
    private ArticlePromptTemplateVersionMapper versionMapper;
    private TemplatePerspectiveService perspectiveService;
    private ArticleTemplateAllocationService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(ArticlePromptTemplateMapper.class);
        versionMapper = mock(ArticlePromptTemplateVersionMapper.class);
        perspectiveService = mock(TemplatePerspectiveService.class);
        when(perspectiveService.resolve(any(), any(), any())).thenAnswer(invocation ->
                TemplatePerspectiveService.ResolvedPerspective.defaultFor(invocation.getArgument(1)));
        service = new ArticleTemplateAllocationService(
                templateMapper,
                versionMapper,
                new QuestionScenePlatformSuggestionService(),
                perspectiveService,
                new ArticlePromptContractResolver(new ObjectMapper()),
                new ArticleTemplateCompatibilityResolver());
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

    @Test
    void v2MissingScenePrefersGenericInsteadOfMixingSceneTemplates() {
        ArticlePromptTemplate brand = template(1L, "brand", 10);
        ArticlePromptTemplate generic = template(2L, null, 10);
        when(templateMapper.selectList(any())).thenReturn(List.of(brand, generic));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, 1L));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, 2L));

        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates =
                service.activeTemplates("forum", null, null);

        assertEquals(List.of(2L), candidates.stream().map(item -> item.template().getId()).toList());
    }

    @Test
    void v2MissingSceneUsesAllCarriersWhenNoGenericTemplateExists() {
        ArticlePromptTemplate brand = template(1L, "brand", 10);
        ArticlePromptTemplate decision = template(2L, "decision", 10);
        when(templateMapper.selectList(any())).thenReturn(List.of(brand, decision));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, 1L));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, 2L));

        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates =
                service.activeTemplates("forum", null, null);

        assertEquals(List.of(1L, 2L), candidates.stream().map(item -> item.template().getId()).toList());
    }

    @Test
    void generationOptionsResolvePerspectiveForEachChannelAndBrand() {
        when(templateMapper.selectList(any())).thenReturn(List.of());

        service.options(88L);

        verify(perspectiveService).resolve(eq(88L), eq("industry_site"), any());
        verify(perspectiveService).resolve(eq(88L), eq("authority_media"), eq("industry_media"));
        verify(perspectiveService).resolve(eq(88L), eq("forum"), any());
    }

    @Test
    void generationOptionsKeepAllV2SceneTemplatesVisibleForCustomSelection() {
        ArticlePromptTemplate brand = template(1L, "brand", 10);
        ArticlePromptTemplate generic = template(2L, null, 10);
        when(templateMapper.selectList(any())).thenReturn(List.of(brand, generic));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, 1L));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, 2L));

        var options = service.options(88L);

        var forum = options.groups().stream()
                .filter(group -> "forum".equals(group.code()))
                .flatMap(group -> group.channels().stream())
                .findFirst()
                .orElseThrow();
        assertEquals(2, forum.templates().size());
    }

    @Test
    void allocateCandidatesKeepsSingleCandidateBehavior() {
        ArticlePromptTemplate only = template(1L, null, 10);

        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated = service.allocateCandidates(
                List.of(new ArticleTemplateAllocationService.TemplateWithVersion(only, version(101L, 1L))),
                4,
                new Random(7L)
        );

        assertEquals(1, allocated.size());
        assertEquals(1L, allocated.get(0).template().getId());
        assertEquals(4, allocated.get(0).count());
    }

    @Test
    void allocateCandidatesSelectsOneWeightedCandidateForSingleArticle() {
        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates = List.of(
                candidate(1L, 10), candidate(2L, 20), candidate(3L, 30));

        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated =
                service.allocateCandidates(candidates, 1, new Random(11L));

        assertEquals(1, allocated.size());
        assertEquals(1, allocated.get(0).count());
        assertTrue(List.of(1L, 2L, 3L).contains(allocated.get(0).template().getId()));
    }

    @Test
    void allocateCandidatesCoversDifferentTemplatesBeforeWeightedReuse() {
        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates = List.of(
                candidate(1L, 10), candidate(2L, 20), candidate(3L, 30));

        List<ArticleTemplateAllocationService.AllocatedTemplate> allocated =
                service.allocateCandidates(candidates, 2, new Random(13L));

        assertEquals(2, allocated.size());
        assertEquals(2, allocated.stream().mapToInt(ArticleTemplateAllocationService.AllocatedTemplate::count).sum());
        assertTrue(allocated.stream().allMatch(item -> item.count() == 1));
    }

    @Test
    void allocateCandidatesExcludesZeroWeightAndIsStableWithFixedRandomSource() {
        List<ArticleTemplateAllocationService.TemplateWithVersion> candidates = List.of(
                candidate(1L, 0), candidate(2L, 10), candidate(3L, 30));

        List<ArticleTemplateAllocationService.AllocatedTemplate> first =
                service.allocateCandidates(candidates, 7, new Random(17L));
        List<ArticleTemplateAllocationService.AllocatedTemplate> second =
                service.allocateCandidates(candidates, 7, new Random(17L));

        assertEquals(first.stream().map(item -> item.template().getId() + ":" + item.count()).toList(),
                second.stream().map(item -> item.template().getId() + ":" + item.count()).toList());
        assertEquals(7, first.stream().mapToInt(ArticleTemplateAllocationService.AllocatedTemplate::count).sum());
        assertFalse(first.stream().anyMatch(item -> item.template().getId().equals(1L)));
    }

    private ArticleTemplateAllocationService.TemplateWithVersion candidate(Long id, int weight) {
        return new ArticleTemplateAllocationService.TemplateWithVersion(
                template(id, null, weight), version(100L + id, id));
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
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        return version;
    }
}
