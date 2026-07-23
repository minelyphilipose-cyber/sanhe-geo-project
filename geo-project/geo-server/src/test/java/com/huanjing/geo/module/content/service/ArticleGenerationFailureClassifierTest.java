package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleGenerationFailureClassifierTest {

    @Test
    void classifiesRetriableInfrastructureFailures() {
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "AI article generation failed: HTTP request timed out after 300000ms")).isTrue();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "LLM permit unavailable: FEATURE:article")).isTrue();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "LLM invoke failed after retries: HTTP 503: unavailable")).isTrue();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "HTTP 429: too many requests")).isTrue();
    }

    @Test
    void keepsContentAndComplianceFailuresOnTheSameModel() {
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "生成内容命中项目禁用表达")).isFalse();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "医疗合规校验未通过")).isFalse();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(null)).isFalse();
    }
}
