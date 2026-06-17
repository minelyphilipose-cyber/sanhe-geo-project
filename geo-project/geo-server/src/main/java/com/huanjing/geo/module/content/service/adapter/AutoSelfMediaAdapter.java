package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;

public interface AutoSelfMediaAdapter extends SelfMediaAdapter {

    default void preflightCredential(SelfMediaAccount account) {
    }

    ValidationResult validate(ArticleDraft article,
                              String contentMarkdown,
                              TargetContext.SelfMediaTarget target);

    SubmitResult submitToTarget(ArticleDraft article,
                                String contentMarkdown,
                                TargetContext.SelfMediaTarget target);

    ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account);
}
