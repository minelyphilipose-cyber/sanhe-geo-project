package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.MpAccount;

public interface SelfMediaAdapter {

    String platform();

    default boolean supportsPlatform(String platform) {
        return platform() != null && platform().equalsIgnoreCase(platform);
    }

    ValidationResult validate(ArticleDraft article,
                              String contentMarkdown,
                              TargetContext.SelfMediaTarget target);

    SubmitResult submitToTarget(ArticleDraft article,
                                String contentMarkdown,
                                TargetContext.SelfMediaTarget target);

    /**
     * @param account TODO Step 2: current phase uses MpAccount; replace with SelfMediaAccount after account table abstraction.
     */
    ReviewStatusResult refreshReviewStatus(DistributionTask task, MpAccount account);
}
