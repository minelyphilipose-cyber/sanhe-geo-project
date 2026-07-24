package com.huanjing.geo.module.mobiledashboard.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MobileDashboardWechatJsSdkPropertiesTest {

    @Test
    void emptyRolloutProjectEnvironmentValueBindsAsEmptyList() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "geo.mobile-dashboard.wechat-js-sdk.rollout-project-ids", "",
                "geo.mobile-dashboard.wechat-js-sdk.enabled", "false"
        ));

        MobileDashboardWechatJsSdkProperties properties = new Binder(source)
                .bindOrCreate(
                        "geo.mobile-dashboard.wechat-js-sdk",
                        Bindable.of(MobileDashboardWechatJsSdkProperties.class)
                );
        properties.validate();

        assertThat(properties.getRolloutProjectIds()).isEmpty();
        assertThat(properties.isEnabledForProject(11L)).isFalse();
    }

    @Test
    void allowlistEnablesOnlyConfiguredProjects() {
        MobileDashboardWechatJsSdkProperties properties = new MobileDashboardWechatJsSdkProperties();
        properties.setEnabled(true);
        properties.setAppId("wx_test");
        properties.setAppSecret("secret");
        properties.setRolloutMode("allowlist");
        properties.setRolloutProjectIds(java.util.List.of(11L));
        properties.validate();

        assertThat(properties.isEnabledForProject(11L)).isTrue();
        assertThat(properties.isEnabledForProject(12L)).isFalse();
    }

    @Test
    void allModeEnablesEveryProjectWithoutAnAllowlist() {
        MobileDashboardWechatJsSdkProperties properties = new MobileDashboardWechatJsSdkProperties();
        properties.setEnabled(true);
        properties.setAppId("wx_test");
        properties.setAppSecret("secret");
        properties.setRolloutMode("all");
        properties.validate();

        assertThat(properties.isEnabledForProject(11L)).isTrue();
        assertThat(properties.isEnabledForProject(999L)).isTrue();
    }
}
