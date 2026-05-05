package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatMpAdapterTest {

    private final WechatMpAdapter adapter = new WechatMpAdapter(null, null, null, null, null, null, null);

    @Test
    void selfMediaIdentity_matchesWechatPlatform() {
        assertEquals("wechat_mp", adapter.platform());
        assertTrue(adapter.supportsPlatform("wechat_mp"));
    }

    @Test
    void refreshReviewStatus_notApplicableForWechatDraftFlow() {
        ReviewStatusResult result = adapter.refreshReviewStatus(null, new SelfMediaAccount());

        assertEquals(ReviewStatusResult.ReviewStatus.NOT_APPLICABLE, result.status());
    }
}
