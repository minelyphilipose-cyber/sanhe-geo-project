package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticlePromptVariableRegistryTest {

    private final ArticlePromptVariableRegistry registry = new ArticlePromptVariableRegistry(new ObjectMapper());

    @Test
    void validateTemplateVariablesRejectsUnknownPlaceholders() {
        assertThatThrownBy(() -> registry.validateTemplateVariables(
                "系统 {{contactBlock}}",
                "正文 {{brandName}} {{brandBasicInfo}}",
                "[\"brandName\"]"
        ))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("brandBasicInfo");
    }

    @Test
    void validateTemplateVariablesRejectsUnknownVariablesJsonEntries() {
        assertThatThrownBy(() -> registry.validateTemplateVariables(
                "系统",
                "正文 {{brandName}}",
                "[\"brandName\", \"brandQualification\"]"
        ))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("brandQualification");
    }

    @Test
    void renderUsesVariableLevelEmptyStrategy() {
        String rendered = registry.render(
                "品牌:{{brandName}} 主营:{{mainBusiness}} 介绍:{{brandIntro}} 联系:{{contactBlock}} 区域:{{region}}",
                Map.of()
        );

        assertThat(rendered)
                .contains("品牌:该品牌")
                .contains("主营:暂未提供明确主营业务资料")
                .contains("介绍:暂未提供品牌介绍")
                .contains("联系: ")
                .contains("区域:-");
    }

    @Test
    void renderRejectsUnknownResidualPlaceholders() {
        assertThatThrownBy(() -> registry.render("正文 {{brandName}} {{brandBasicInfo}}", Map.of("brandName", "得闲spa")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("brandBasicInfo");
    }

    @Test
    void listIncludesRendererSupportedTitleVariables() {
        assertThat(registry.definitionMap())
                .containsKeys("titleGuide", "titleElements", "projectName", "channelName", "articleTypeName");
    }
}
