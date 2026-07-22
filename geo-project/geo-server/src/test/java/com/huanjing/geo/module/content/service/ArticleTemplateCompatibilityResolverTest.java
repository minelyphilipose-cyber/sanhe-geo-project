package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTemplateCompatibilityResolverTest {

    private final ArticleTemplateCompatibilityResolver resolver = new ArticleTemplateCompatibilityResolver();

    @Test
    void explicitSceneUsesExactThenGeneralThenCarrier() {
        var exact = candidate(1L, "qa");
        var general = candidate(2L, null);
        var carrier = candidate(3L, "brand");

        assertThat(resolver.preferredCandidates(List.of(exact, general, carrier), "qa"))
                .extracting(item -> item.template().getId()).containsExactly(1L);
        assertThat(resolver.preferredCandidates(List.of(general, carrier), "qa"))
                .extracting(item -> item.template().getId()).containsExactly(2L);
        assertThat(resolver.preferredCandidates(List.of(carrier), "qa"))
                .extracting(item -> item.template().getId()).containsExactly(3L);
    }

    @Test
    void missingScenePrefersGeneralAndUsesCarriersOnlyWhenNeeded() {
        var general = candidate(2L, null);
        var firstCarrier = candidate(3L, "brand");
        var secondCarrier = candidate(4L, "decision");

        assertThat(resolver.preferredCandidates(List.of(firstCarrier, general), null))
                .extracting(item -> item.template().getId()).containsExactly(2L);
        assertThat(resolver.preferredCandidates(List.of(firstCarrier, secondCarrier), null))
                .extracting(item -> item.template().getId()).containsExactly(3L, 4L);
    }

    private ArticleTemplateAllocationService.TemplateWithVersion candidate(Long id, String scene) {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(id);
        template.setQuestionSceneCode(scene);
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setId(100L + id);
        version.setTemplateId(id);
        return new ArticleTemplateAllocationService.TemplateWithVersion(template, version);
    }
}
