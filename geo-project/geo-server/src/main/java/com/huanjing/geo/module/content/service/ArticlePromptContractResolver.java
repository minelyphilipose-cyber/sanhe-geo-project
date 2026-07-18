package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ArticlePromptContractResolver {

    public static final String CONTRACT_V2 = "v2";

    private final ObjectMapper objectMapper;

    public boolean isV2(ArticlePromptTemplateVersion version) {
        if (version == null || !StringUtils.hasText(version.getQualityRulesJson())) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(version.getQualityRulesJson());
            return CONTRACT_V2.equals(root.path("promptContract").asText());
        } catch (Exception ignored) {
            return false;
        }
    }
}
