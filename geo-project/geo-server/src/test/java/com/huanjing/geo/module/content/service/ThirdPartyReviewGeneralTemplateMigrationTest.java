package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThirdPartyReviewGeneralTemplateMigrationTest {

    @Test
    void seedsV2GeneralReviewTemplateForEverySelfMediaPlatform() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V371__third_party_review_general_templates.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("'promptContract', 'v2'")
                    .contains("t.question_scene_code = NULL")
                    .contains("'review_recommend'");
            assertThat(sql).contains(
                    "'wechat'",
                    "'baijiahao'",
                    "'zhihu'",
                    "'toutiao'",
                    "'netease'",
                    "'sohu'",
                    "'xiaohongshu'",
                    "'douyin'"
            );
        }
    }
}
