package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;

public record LocalAgentSelfMediaPublishCheckClaimResponse(
        SelfMediaPublishScheduleVO schedule,
        Launch launch
) {
    public record Launch(
            Long scheduleId,
            String platform,
            String url,
            Long selfMediaAccountId,
            Long browserEnvironmentAccountId,
            String expectedPlatformAccountId,
            String expectedAccountName,
            String environmentKey,
            String environmentName,
            String providerProfileId
    ) {
    }
}
