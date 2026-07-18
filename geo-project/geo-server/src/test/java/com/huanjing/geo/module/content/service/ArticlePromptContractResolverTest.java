package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptContractResolverTest {

    private final ArticlePromptContractResolver resolver = new ArticlePromptContractResolver(new ObjectMapper());

    @Test
    void onlyValidV2MarkerEnablesV2Contract() {
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        version.setQualityRulesJson("{\"promptContract\":\"v2\"}");
        assertTrue(resolver.isV2(version));

        version.setQualityRulesJson("{\"promptContract\":\"v1\"}");
        assertFalse(resolver.isV2(version));
        version.setQualityRulesJson("not-json");
        assertFalse(resolver.isV2(version));
        assertFalse(resolver.isV2(null));
    }
}
