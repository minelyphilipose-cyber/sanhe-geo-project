package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPublishFailureCodesTest {

    @Test
    void wechatApiUnauthorizedHasActionableMetadata() {
        assertEquals("WECHAT_API_UNAUTHORIZED",
                SelfMediaPublishFailureCodes.classifyByMessage("api unauthorized rid: rid-from-wechat"));
        assertEquals("微信公众号发布权限不足",
                SelfMediaPublishFailureCodes.label("WECHAT_API_UNAUTHORIZED"));
        assertEquals("重新授权公众号",
                SelfMediaPublishFailureCodes.actionLabel("WECHAT_API_UNAUTHORIZED"));
        assertFalse(SelfMediaPublishFailureCodes.retryable("WECHAT_API_UNAUTHORIZED"));
    }

    @Test
    void platformTabLifecycleFailuresAreRetryable() {
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("PLATFORM_TAB_GONE"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("PLATFORM_TAB_REDIRECTED"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("DOUYIN_PUBLISH_NOT_CONFIRMED"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("TOUTIAO_PUBLISH_NOT_CONFIRMED"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("XIAOHONGSHU_PUBLISH_NOT_CONFIRMED"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("ZHIHU_PUBLISH_NOT_CONFIRMED"));
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("BAIJIAHAO_PUBLISH_NOT_CONFIRMED"));
        assertEquals("立即重试", SelfMediaPublishFailureCodes.actionLabel("PLATFORM_TAB_GONE"));
        assertEquals("重新校验", SelfMediaPublishFailureCodes.actionLabel("DOUYIN_PUBLISH_NOT_CONFIRMED"));
    }
}
