package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;

/**
 * Fallback adapter used while platform-specific schedule adapters are not implemented.
 */
public class NoopSelfMediaPublishScheduleAdapter implements SelfMediaPublishScheduleAdapter {

    @Override
    public boolean supports(String platform) {
        return true;
    }

    @Override
    public ScheduleExecutionResult schedule(SelfMediaPublishScheduleVO schedule) {
        return ScheduleExecutionResult.failed(
                "ADAPTER_NOT_IMPLEMENTED",
                "当前平台尚未接入自动定时发布适配器",
                "{\"adapter\":\"noop\"}"
        );
    }

    @Override
    public PublishCheckResult checkPublishResult(SelfMediaPublishScheduleVO schedule) {
        return PublishCheckResult.unknown("{\"adapter\":\"noop\"}");
    }
}
