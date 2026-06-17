package com.huanjing.geo.module.content.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
