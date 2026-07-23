package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleModelResolverTest {
    private AiPlatformConfigMapper configMapper;
    private PlatformCredentialService credentialService;
    private ArticleModelResolver resolver;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiPlatformConfig.class);
        configMapper = mock(AiPlatformConfigMapper.class);
        credentialService = mock(PlatformCredentialService.class);
        resolver = new ArticleModelResolver(configMapper, credentialService);
        ReflectionTestUtils.setField(resolver, "articleExcludedPlatformCodes", "hunyuan,yuanbao");
    }

    @Test
    void resolveUsesRemainingArticlePlatformWhenExcludedPlatformsAreConfigured() {
        AiPlatformConfig qwen = platform("qwen");
        when(configMapper.selectOne(any())).thenReturn(qwen);
        when(credentialService.resolveApiKey(eq("qwen"), any(), any())).thenReturn("sk-qwen");

        ArticleModelResolver.ModelSelection selection = resolver.resolve(null, null, "system", true);

        assertThat(selection.platformCode()).isEqualTo("qwen");
        assertThat(selection.config().temperature()).isEqualTo(ArticleGenerationTemperatures.DEFAULT);
    }

    @Test
    void resolveUsesExplicitV2TemperatureForDirectCalls() {
        AiPlatformConfig qwen = platform("qwen");
        when(configMapper.selectOne(any())).thenReturn(qwen);
        when(credentialService.resolveApiKey(eq("qwen"), any(), any())).thenReturn("sk-qwen");

        ArticleModelResolver.ModelSelection selection = resolver.resolve(
                null, null, "system", true, ArticleGenerationTemperatures.V2_STANDARD);

        assertThat(selection.config().temperature()).isEqualTo(ArticleGenerationTemperatures.V2_STANDARD);
    }

    @Test
    void resolveUsesFiveMinuteTimeoutForLongFormArticles() {
        AiPlatformConfig qwen = platform("qwen");
        qwen.setTimeoutMs(60_000);
        when(configMapper.selectOne(any())).thenReturn(qwen);
        when(credentialService.resolveApiKey(eq("qwen"), any(), any())).thenReturn("sk-qwen");

        ArticleModelResolver.ModelSelection selection = resolver.resolve("qwen", null, "system", true);

        assertThat(selection.config().requestTimeoutMs()).isEqualTo(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        assertThat(selection.config().requestTimeoutMaxMs()).isEqualTo(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        assertThat(selection.config().maxRetry()).isZero();
    }

    @Test
    void resolveHonorsConfiguredLongFormTimeoutWithoutEnablingRetries() {
        ReflectionTestUtils.setField(resolver, "articleRequestTimeoutMs", 240_000);
        AiPlatformConfig qwen = platform("qwen");
        qwen.setTimeoutMs(60_000);
        when(configMapper.selectOne(any())).thenReturn(qwen);
        when(credentialService.resolveApiKey(eq("qwen"), any(), any())).thenReturn("sk-qwen");

        ArticleModelResolver.ModelSelection selection = resolver.resolve("qwen", null, "system", true);

        assertThat(selection.config().requestTimeoutMs()).isEqualTo(240_000);
        assertThat(selection.config().requestTimeoutMaxMs()).isEqualTo(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        assertThat(selection.config().maxRetry()).isZero();
    }

    @Test
    void resolveForBatchDistributesStableSelectionsAcrossEligiblePlatforms() {
        AiPlatformConfig doubao = platform(1L, "doubao", 2);
        AiPlatformConfig deepseek = platform(2L, "deepseek", 1);
        AiPlatformConfig qwen = platform(3L, "qwen", 1);
        when(configMapper.selectList(any())).thenReturn(List.of(doubao, deepseek, qwen));
        when(credentialService.resolveApiKey(anyString(), any(), any())).thenReturn("sk-platform");

        Set<String> selectedPlatforms = LongStream.rangeClosed(1L, 80L)
                .mapToObj(key -> resolver.resolveForBatch(
                        key, "system", true, ArticleGenerationTemperatures.V2_STANDARD, Set.of()))
                .map(ArticleModelResolver.ModelSelection::platformCode)
                .collect(Collectors.toSet());
        ArticleModelResolver.ModelSelection first = resolver.resolveForBatch(
                17L, "system", true, ArticleGenerationTemperatures.V2_STANDARD, Set.of());
        ArticleModelResolver.ModelSelection repeated = resolver.resolveForBatch(
                17L, "system", true, ArticleGenerationTemperatures.V2_STANDARD, Set.of());

        assertThat(selectedPlatforms).containsExactlyInAnyOrder("doubao", "deepseek", "qwen");
        assertThat(repeated.platformCode()).isEqualTo(first.platformCode());
        assertThat(repeated.modelId()).isEqualTo(first.modelId());
    }

    @Test
    void resolveForBatchExcludesPreviousAndAdministrativelyUnavailablePlatforms() {
        AiPlatformConfig doubao = platform(1L, "doubao", 2);
        AiPlatformConfig qwen = platform(2L, "qwen", 1);
        AiPlatformConfig ernie = platform(3L, "ernie", 1);
        ernie.setCurrentHealthStatus("maintenance");
        when(configMapper.selectList(any())).thenReturn(List.of(doubao, qwen, ernie));
        when(credentialService.resolveApiKey(anyString(), any(), any())).thenReturn("sk-platform");

        ArticleModelResolver.ModelSelection selection = resolver.resolveForBatch(
                9L, "system", true, ArticleGenerationTemperatures.V2_STANDARD, Set.of("DOUBAO"));

        assertThat(selection.platformCode()).isEqualTo("qwen");
    }

    @Test
    void resolveFailsWhenExcludedPlatformWouldBeTheOnlyCandidate() {
        when(configMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve("hunyuan", null, "system", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI article model config missing");
    }

    private AiPlatformConfig platform(String code) {
        return platform(null, code, 1);
    }

    private AiPlatformConfig platform(Long id, String code, int concurrencyLimit) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setId(id);
        config.setPlatformCode(code);
        config.setPlatformName(code);
        config.setEnabled(true);
        config.setEnabledForArticle(true);
        config.setModelId(code + "-model");
        config.setApiUrl("https://example.com/v1/chat/completions");
        config.setApiKey("sk-raw");
        config.setConcurrencyLimit(concurrencyLimit);
        return config;
    }
}
