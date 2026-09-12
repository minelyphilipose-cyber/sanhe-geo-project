package com.huanjing.geo.module.content.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSelfMediaScheduleItemMapperSqlTest {

    @Test
    void imageApplyRecoveryUsesExplicitDatabaseUpdatesForBothQuotaStates() throws Exception {
        Method reserved = ProjectSelfMediaScheduleItemMapper.class.getMethod(
                "restoreReservedDouyinScheduleAfterImagesApplied", Long.class, Long.class,
                java.time.LocalDateTime.class);
        String reservedSql = String.join("\n", reserved.getAnnotation(Update.class).value());
        assertTrue(reservedSql.contains("i.schedule_error_code = NULL"));
        assertTrue(reservedSql.contains("i.quota_reservation_status = 'reserved'"));
        assertTrue(reservedSql.contains("b.status = 'planned'"));

        Method refunded = ProjectSelfMediaScheduleItemMapper.class.getMethod(
                "markRefundedDouyinScheduleForManualReschedule", Long.class, Long.class,
                java.time.LocalDateTime.class);
        String refundedSql = String.join("\n", refunded.getAnnotation(Update.class).value());
        assertTrue(refundedSql.contains("quota_reservation_status = 'refunded'"));
        assertTrue(refundedSql.contains("重新处理本批次"));
    }
}
