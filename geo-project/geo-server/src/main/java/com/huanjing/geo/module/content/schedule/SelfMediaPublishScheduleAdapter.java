package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;

public interface SelfMediaPublishScheduleAdapter {

    boolean supports(String platform);

    ScheduleExecutionResult schedule(SelfMediaPublishScheduleVO schedule);

    PublishCheckResult checkPublishResult(SelfMediaPublishScheduleVO schedule);
}
