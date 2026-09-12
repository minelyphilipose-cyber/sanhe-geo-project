package com.huanjing.geo.module.content.mapper;

import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
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
        assertTrue(refundedSql.contains("SET status = 'schedule_failed'"));
        assertTrue(refundedSql.contains("quota_reservation_status = 'refunded'"));
        assertTrue(refundedSql.contains("status = 'cancelled'"));
        assertTrue(refundedSql.contains("cancelled_by IS NULL"));
        assertTrue(refundedSql.contains("排期失败超过可重试时限，系统已释放预占额度"));
        assertTrue(refundedSql.contains("cancelled_at = NULL"));
        assertTrue(refundedSql.contains("cancellation_reason = NULL"));
        assertTrue(refundedSql.contains("重新处理本批次"));
    }

    @Test
    void recoveryScanIncludesOnlySystemRefundedCancelledItemsWithAppliedImages() throws Exception {
        Method method = ProjectSelfMediaScheduleBatchMapper.class.getMethod(
                "selectRecoverableDouyinImageFailures", int.class);
        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("LEFT JOIN article_draft a"));
        assertTrue(sql.contains("LEFT JOIN douyin_article_image_batch image_batch"));
        assertTrue(sql.contains("b.status NOT IN ('paused', 'skipped')"));
        assertTrue(sql.contains("i.status = 'cancelled'"));
        assertTrue(sql.contains("i.quota_reservation_status = 'refunded'"));
        assertTrue(sql.contains("i.cancelled_by IS NULL"));
        assertTrue(sql.contains("排期失败超过可重试时限，系统已释放预占额度"));
        assertTrue(sql.contains("COALESCE(a.status, '') <> 'deleted'"));
        assertTrue(sql.contains("image_batch.article_id = a.id"));
        assertTrue(sql.contains("image_batch.status = 'applied'"));
        assertTrue(sql.contains("image_batch.applied_version_no = a.current_version_no"));
    }
}
