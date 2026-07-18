package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    }

    @Test
    void resolveUsesThreeMinuteTimeoutForLongFormArticles() {
        AiPlatformConfig qwen = platform("qwen");
        qwen.setTimeoutMs(60_000);
        when(configMapper.selectOne(any())).thenReturn(qwen);
        when(credentialService.resolveApiKey(eq("qwen"), any(), any())).thenReturn("sk-qwen");

        ArticleModelResolver.ModelSelection selection = resolver.resolve("qwen", null, "system", true);

        assertThat(selection.config().requestTimeoutMs()).isEqualTo(180_000);
        assertThat(selection.config().requestTimeoutMaxMs()).isEqualTo(180_000);
        assertThat(selection.config().maxRetry()).isZero();
    }

    @Test
    void resolveFailsWhenExcludedPlatformWouldBeTheOnlyCandidate() {
        when(configMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve("hunyuan", null, "system", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("AI article model config missing");
    }

    private AiPlatformConfig platform(String code) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode(code);
        config.setPlatformName(code);
        config.setEnabled(true);
        config.setEnabledForArticle(true);
        config.setModelId(code + "-model");
        config.setApiUrl("https://example.com/v1/chat/completions");
        config.setApiKey("sk-raw");
        config.setConcurrencyLimit(1);
        return config;
    }
}
