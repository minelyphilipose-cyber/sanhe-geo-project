package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleEnvironmentLock;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SelfMediaPublishScheduleEnvironmentLockMapper
        extends BaseMapper<SelfMediaPublishScheduleEnvironmentLock> {

    @Insert("""
            INSERT INTO self_media_publish_schedule_environment_lock
                (browser_environment_id, schedule_id, locked_until, created_at, updated_at)
            VALUES
                (#{browserEnvironmentId}, #{scheduleId}, #{lockedUntil}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                schedule_id = IF(locked_until < #{now}, VALUES(schedule_id), schedule_id),
                locked_until = IF(locked_until < #{now}, VALUES(locked_until), locked_until),
                updated_at = IF(locked_until < #{now}, VALUES(updated_at), updated_at)
            """)
    int upsertIfExpired(@Param("browserEnvironmentId") Long browserEnvironmentId,
                        @Param("scheduleId") Long scheduleId,
                        @Param("lockedUntil") LocalDateTime lockedUntil,
                        @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
            FROM self_media_publish_schedule_environment_lock
            WHERE browser_environment_id = #{browserEnvironmentId}
            LIMIT 1
            """)
    SelfMediaPublishScheduleEnvironmentLock selectByEnvironmentId(@Param("browserEnvironmentId") Long browserEnvironmentId);

    @Delete("""
            DELETE FROM self_media_publish_schedule_environment_lock
            WHERE schedule_id = #{scheduleId}
            """)
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
