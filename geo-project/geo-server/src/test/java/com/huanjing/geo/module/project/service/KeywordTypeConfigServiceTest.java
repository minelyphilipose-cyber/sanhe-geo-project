package com.huanjing.geo.module.project.service;

import com.huanjing.geo.module.project.dto.KeywordTypeConfigVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordTypeConfigServiceTest {

    private final KeywordTypeConfigService service = new KeywordTypeConfigService();

    @Test
    void decisionConfig_displaysIndustryButDoesNotRequireIt() {
        KeywordTypeConfigVO config = service.getConfig("decision");

        assertThat(config.isIndustryRequired()).isFalse();
        assertThat(config.getRequiredColumns().isIndustry()).isFalse();
        assertThat(config.getColumns().isIndustry()).isTrue();
    }
}
