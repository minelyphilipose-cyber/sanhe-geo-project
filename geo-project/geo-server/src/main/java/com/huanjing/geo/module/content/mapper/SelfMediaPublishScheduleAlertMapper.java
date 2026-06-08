package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SelfMediaPublishScheduleAlertMapper extends BaseMapper<SelfMediaPublishScheduleAlert> {

    @Select("""
            SELECT *
            FROM self_media_publish_schedule_alert
            WHERE schedule_id = #{scheduleId}
              AND status = 'open'
            ORDER BY
              CASE severity
                WHEN 'critical' THEN 1
                WHEN 'warning' THEN 2
                ELSE 3
              END,
              last_seen_at DESC,
              id DESC
            """)
    List<SelfMediaPublishScheduleAlert> selectOpenByScheduleId(@Param("scheduleId") Long scheduleId);

    @Select("""
            SELECT *
            FROM self_media_publish_schedule_alert
            WHERE active_key = #{activeKey}
            LIMIT 1
            """)
    SelfMediaPublishScheduleAlert selectByActiveKey(@Param("activeKey") String activeKey);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule_alert
            WHERE schedule_id IN
              <foreach collection="scheduleIds" item="scheduleId" open="(" separator="," close=")">
                #{scheduleId}
              </foreach>
              AND status = 'open'
            ORDER BY schedule_id ASC,
              CASE severity
                WHEN 'critical' THEN 1
                WHEN 'warning' THEN 2
                ELSE 3
              END,
              last_seen_at DESC,
              id DESC
            </script>
            """)
    List<SelfMediaPublishScheduleAlert> selectOpenByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}
