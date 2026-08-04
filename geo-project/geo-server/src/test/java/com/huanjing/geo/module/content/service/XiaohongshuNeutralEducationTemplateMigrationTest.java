package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.XiaohongshuArticlePolicies;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XiaohongshuNeutralEducationTemplateMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V337__special_industry_xiaohongshu_neutral_education_template.sql");

    @Test
    void createsIndependentV2TemplateAndSwitchesOnlyTheRoute() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(XiaohongshuArticlePolicies.NEUTRAL_EDUCATION_TEMPLATE_NAME);
        assertThat(sql).contains("'promptContract', 'v2'");
        assertThat(sql).contains("'xiaohongshuContentMode', 'neutral_education'");
        assertThat(sql).contains("'brandMentionMin', 0");
        assertThat(sql).contains("'brandMentionMax', 0");
        assertThat(sql).contains("INSERT INTO special_industry_template_route");
        assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(sql).doesNotContain("DELETE FROM article_prompt_template");
        assertThat(sql).doesNotContain("DELETE FROM article_prompt_template_version");
    }
}
