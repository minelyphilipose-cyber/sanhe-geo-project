package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileDashboardAiPlatformCatalogTest {

    @Test
    void defaultsToCurrentProductionPlatformsBeforeDatabaseRefresh() {
        MobileDashboardAiPlatformCatalog catalog =
                new MobileDashboardAiPlatformCatalog(mock(AiPlatformConfigMapper.class));

        assertThat(catalog.scope().canonicalCodes())
                .containsExactly("doubao", "deepseek", "tongyi");
        assertThat(catalog.scope().aliasSql()).doesNotContain("wenxin_web");
    }

    @Test
    void refreshMakesWenxinVisibleOnlyWhenItsWebProfileFlagIsEnabled() {
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                visibleProfile("doubao_web", "doubao"),
                visibleProfile("wenxin_web", "wenxin")
        ));
        MobileDashboardAiPlatformCatalog catalog = new MobileDashboardAiPlatformCatalog(mapper);

        catalog.refresh();

        assertThat(catalog.scope().canonicalCodes()).containsExactly("doubao", "wenxin");
        assertThat(catalog.scope().aliasSql())
                .contains("'doubao_web'")
                .contains("'wenxin_web'")
                .contains("'ernie'")
                .doesNotContain("'qwen_web'");
    }

    @Test
    void wenxinAliasesShareOneCanonicalCodeAndDisplayOrder() {
        MobileDashboardAiPlatformCatalog catalog =
                new MobileDashboardAiPlatformCatalog(mock(AiPlatformConfigMapper.class));

        assertThat(catalog.canonicalCode("ernie")).isEqualTo("wenxin");
        assertThat(catalog.canonicalCode("wenxin_web")).isEqualTo("wenxin");
        assertThat(catalog.aliasSql("wenxin"))
                .isEqualTo("'wenxin','ernie','wenxin_web'");
        assertThat(catalog.order("wenxin")).isGreaterThan(catalog.order("tongyi"));
    }

    private AiPlatformConfig visibleProfile(String platformCode, String channelCode) {
        AiPlatformConfig profile = new AiPlatformConfig();
        profile.setPlatformCode(platformCode);
        profile.setChannelCode(channelCode);
        profile.setUsageScene("QUESTION_POLL_WEB");
        profile.setEnabledForMobileDashboard(true);
        return profile;
    }
}
