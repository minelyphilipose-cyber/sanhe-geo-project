package com.huanjing.geo.module.extension.dto;

import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;

public record LocalAgentSelfMediaScheduleClaimResponse(
        SelfMediaPublishScheduleVO schedule,
        DistributionTask task,
        Launch launch,
        String claimBlockedReason
) {
    public record Launch(
            Long taskId,
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
