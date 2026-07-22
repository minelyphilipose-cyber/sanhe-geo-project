package com.huanjing.geo.module.content.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleEditorialMissionResolverTest {

    private final ArticleEditorialMissionResolver resolver = new ArticleEditorialMissionResolver();

    @Test
    void sceneDeterminesMissionAndArticleTypeOnlyAddsFormDirection() {
        ArticleEditorialMission mission = resolver.resolve("decision", "social_note");

        assertThat(mission.sceneCode()).isEqualTo("decision");
        assertThat(mission.missionText())
                .contains("真实决策矛盾", "图文快速阅读")
                .doesNotContain("第一段", "固定结构");
    }

    @Test
    void supportsAllBusinessScenesWithoutGuessingFromTopic() {
        assertThat(resolver.resolve("qa", "faq").missionText()).contains("核心疑问");
        assertThat(resolver.resolve("deal", "stage_advice").missionText()).contains("合作判断或成交前提");
        assertThat(resolver.resolve("brand", "industry_article").missionText()).contains("品牌公开事实");
        assertThat(resolver.resolve("compare", "comparison").missionText()).contains("有意义的差异");
        assertThat(resolver.resolve("function", "scenario_content").missionText()).contains("能力、功能或服务");
        assertThat(resolver.resolve(null, "industry_article").missionText()).contains("最有信息价值的关系");
    }
}
