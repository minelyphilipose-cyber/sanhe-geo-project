package com.huanjing.geo.module.retention.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PollRetentionLatestResultIdentityTest {

    @Test
    void latestResultChannelMatchUsesDashboardCanonicalChannelWithoutPlatformId() {
        String sql = PollRetentionDryRunService.latestResultChannelMatchSql("newer", "current");

        assertThat(sql)
                .contains("newer.channel_code")
                .contains("current.channel_code")
                .contains("doubao_web")
                .contains("qwen_web")
                .contains("<=>")
                .doesNotContain("platform_id");
    }
}
