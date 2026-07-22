package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleQuestionSceneResolverTest {

    private final ArticleQuestionSceneResolver resolver = new ArticleQuestionSceneResolver();

    @Test
    void preservesExplicitRequestedScene() {
        ArticlePromptTemplate template = template("brand");

        ArticleQuestionSceneResolution result = resolver.resolve("qa", template, true);

        assertThat(result.requestedSceneCode()).isEqualTo("qa");
        assertThat(result.effectiveSceneCode()).isEqualTo("qa");
        assertThat(result.source()).isEqualTo(ArticleQuestionSceneResolver.SOURCE_REQUEST);
    }

    @Test
    void customTemplateCanSupplySceneWhenRequestDoesNot() {
        ArticleQuestionSceneResolution result = resolver.resolve(null, template("decision"), true);

        assertThat(result.effectiveSceneCode()).isEqualTo("decision");
        assertThat(result.source()).isEqualTo(ArticleQuestionSceneResolver.SOURCE_CUSTOM_TEMPLATE);
    }

    @Test
    void autoTemplateNeverOverridesMissingRequestedScene() {
        ArticleQuestionSceneResolution result = resolver.resolve(null, template("brand"), false);

        assertThat(result.effectiveSceneCode()).isEqualTo(ArticleQuestionSceneResolver.GENERAL);
        assertThat(result.source()).isEqualTo(ArticleQuestionSceneResolver.SOURCE_GENERAL_FALLBACK);
    }

    private ArticlePromptTemplate template(String scene) {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setQuestionSceneCode(scene);
        return template;
    }
}
