package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleGenerationFailureClassifierTest {

    @Test
    void classifiesRetriableInfrastructureFailures() {
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "AI article generation failed: HTTP request timed out after 300000ms")).isTrue();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "LLM permit unavailable: FEATURE:article")).isFalse();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "LLM invoke failed after retries: HTTP 503: unavailable")).isTrue();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "HTTP 429: too many requests")).isTrue();
    }

    @Test
    void classifiesTemporaryLocalCapacitySeparatelyFromProviderFailure() {
        ArticleGenerationFailureClassifier.Classification classification =
                ArticleGenerationFailureClassifier.classify(
                        new RuntimeException("LLM permit unavailable: FEATURE:article"));

        assertThat(classification.capacityDeferred()).isTrue();
        assertThat(classification.providerRetryable()).isFalse();
    }

    @Test
    void keepsContentAndComplianceFailuresOnTheSameModel() {
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "生成内容命中项目禁用表达")).isFalse();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(
                "医疗合规校验未通过")).isFalse();
        assertThat(ArticleGenerationFailureClassifier.isInfrastructureFailure(null)).isFalse();
    }

    @Test
    void unwrapsAllFailedBeforeDecidingWhetherToRetry() {
        for (String detail : new String[]{
                "模型请求失败，HTTP 401",
                "模型请求失败，HTTP 400；错误码：InputTextSensitiveContentDetected",
                "模型请求失败，HTTP 400；错误码：InvalidParameter"
        }) {
            LlmRouteException wrapped = new LlmRouteException(
                    LlmRouteFailureKind.ALL_FAILED,
                    "All LLM candidates failed",
                    1,
                    new BizException(502, detail));

            ArticleGenerationFailureClassifier.Classification classification =
                    ArticleGenerationFailureClassifier.classify(wrapped);

            assertThat(classification.providerRetryable()).as(detail).isFalse();
            assertThat(classification.capacityDeferred()).as(detail).isFalse();
        }
    }

    @Test
    void classifiesNetworkFailuresByCauseWhenMessagesAreEmpty() {
        assertThat(ArticleGenerationFailureClassifier.classify(new ConnectException()).providerRetryable())
                .isTrue();
        assertThat(ArticleGenerationFailureClassifier.classify(
                new RuntimeException(new ConnectException())).providerRetryable()).isTrue();
        assertThat(ArticleGenerationFailureClassifier.classify(new LlmRouteException(
                LlmRouteFailureKind.ALL_FAILED,
                "All LLM candidates failed",
                1,
                new ConnectException())).providerRetryable()).isTrue();

        assertThat(ArticleGenerationFailureClassifier.classify(
                new SSLHandshakeException("PKIX path building failed")).providerRetryable()).isFalse();
        assertThat(ArticleGenerationFailureClassifier.classify(
                new UnknownHostException("bad-host.invalid")).providerRetryable()).isFalse();
    }

    @Test
    void keepsRateLimitRetryableDespiteBeingHttpFourHundredClass() {
        assertThat(ArticleGenerationFailureClassifier.classify(
                new RuntimeException("HTTP 429: too many requests")).providerRetryable()).isTrue();
    }
}
