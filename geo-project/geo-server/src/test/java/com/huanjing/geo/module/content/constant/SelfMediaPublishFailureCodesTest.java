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
        assertTrue(SelfMediaPublishFailureCodes.isScheduleExecutionRetryable("EXTENSION_CLAIM_TIMEOUT"));
        assertEquals("立即重试", SelfMediaPublishFailureCodes.actionLabel("PLATFORM_TAB_GONE"));
        assertEquals("重新校验", SelfMediaPublishFailureCodes.actionLabel("DOUYIN_PUBLISH_NOT_CONFIRMED"));
        assertEquals("重新校验", SelfMediaPublishFailureCodes.actionLabel("EXTENSION_CLAIM_TIMEOUT"));
    }

    @Test
    void douyinUnpublishedDraftRequiresNonDestructiveManualResolution() {
        assertEquals("DOUYIN_UNPUBLISHED_DRAFT_BLOCKED",
                SelfMediaPublishFailureCodes.classifyByMessage(
                        "DOUYIN_UNPUBLISHED_DRAFT_BLOCKED：检测到抖音账号存在上次未发布图文"));
        assertEquals("抖音存在未完成图文草稿",
                SelfMediaPublishFailureCodes.label("DOUYIN_UNPUBLISHED_DRAFT_BLOCKED"));
        assertFalse(SelfMediaPublishFailureCodes.retryable("DOUYIN_UNPUBLISHED_DRAFT_BLOCKED"));
        assertEquals("查看处理说明",
                SelfMediaPublishFailureCodes.actionLabel("DOUYIN_UNPUBLISHED_DRAFT_BLOCKED"));
    }

    @Test
    void postSubmissionVerificationFailuresAreNeverFreshPublishRetries() {
        assertTrue(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("WORKS_LIST_VERIFY_TIMEOUT"));
        assertTrue(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("TOUTIAO_PUBLISH_NOT_CONFIRMED"));
        assertTrue(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("douyin_publish_not_confirmed"));
        assertTrue(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("ZHIHU_PUBLISH_NOT_SUBMITTED"));
        assertTrue(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("ZHIHU_PUBLISH_NOT_CONFIRMED"));
        assertFalse(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("PAGE_LOAD_TIMEOUT"));
        assertFalse(SelfMediaPublishFailureCodes.isPostSubmissionVerificationFailure("COVER_UPLOAD_TIMEOUT"));
    }

    @Test
    void localAgentPublishCheckTerminalWhitelistIsExact() {
        assertTrue(SelfMediaPublishFailureCodes.isLocalAgentPublishCheckTerminalFailure(
                SelfMediaPublishFailureCodes.BAIJIAHAO_REVIEW_REJECTED));
        assertTrue(SelfMediaPublishFailureCodes.isLocalAgentPublishCheckTerminalFailure(
                SelfMediaPublishFailureCodes.BAIJIAHAO_WORK_WITHDRAWN));
        assertTrue(SelfMediaPublishFailureCodes.isLocalAgentPublishCheckTerminalFailure(
                SelfMediaPublishFailureCodes.DOUYIN_REVIEW_REJECTED));
        assertFalse(SelfMediaPublishFailureCodes.isLocalAgentPublishCheckTerminalFailure(
                "OFFICIAL_API_REVIEW_REJECTED"));
        assertFalse(SelfMediaPublishFailureCodes.isLocalAgentPublishCheckTerminalFailure(
                "PUBLISH_RESULT_CHECK_HELPER_FAILED"));
        assertEquals("本地回查环境异常",
                SelfMediaPublishFailureCodes.label("PUBLISH_RESULT_CHECK_HELPER_FAILED"));
        assertEquals("重新校验",
                SelfMediaPublishFailureCodes.actionLabel("PUBLISH_RESULT_CHECK_HELPER_FAILED"));
    }
}
