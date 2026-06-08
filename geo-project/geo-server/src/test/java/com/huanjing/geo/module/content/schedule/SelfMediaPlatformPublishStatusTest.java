package com.huanjing.geo.module.content.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelfMediaPlatformPublishStatusTest {

    @Test
    void mapsCommonPlatformSignalsToUnifiedStatus() {
        assertEquals(SelfMediaPlatformPublishStatus.SCHEDULED,
                SelfMediaPlatformPublishStatus.fromSignal("将于 06-05 13:15 发布 审核中"));
        assertEquals(SelfMediaPlatformPublishStatus.PUBLISHED,
                SelfMediaPlatformPublishStatus.fromSignal("已发布 查看数据"));
        assertEquals(SelfMediaPlatformPublishStatus.FAILED,
                SelfMediaPlatformPublishStatus.fromSignal("审核失败 未通过"));
        assertEquals(SelfMediaPlatformPublishStatus.UNKNOWN,
                SelfMediaPlatformPublishStatus.fromSignal(null));
    }
}
