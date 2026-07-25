package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.dto.PlatformHealthAggregateRow;
import com.huanjing.geo.module.dispatch.entity.AiPlatformHealthEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiPlatformHealthEventMapper extends BaseMapper<AiPlatformHealthEvent> {
    @Select("""
            SELECT id,
                   platform_code AS platformCode,
                   feature,
                   event_type AS eventType,
                   duration_ms AS durationMs,
                   occurred_at AS occurredAt
            FROM ai_platform_health_event
            WHERE platform_code = #{platformCode}
              AND feature = #{feature}
              AND occurred_at >= #{since}
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<AiPlatformHealthEvent> selectRecentForFeature(@Param("platformCode") String platformCode,
                                                      @Param("feature") String feature,
                                                      @Param("since") LocalDateTime since,
                                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
                platform_code AS platformCode,
                SUM(CASE WHEN event_type IN ('success', 'slow_response') THEN 1 ELSE 0 END) AS invocationCount,
                SUM(CASE WHEN event_type IN ('success', 'slow_response') THEN 1 ELSE 0 END) AS successCount,
                SUM(CASE WHEN event_type = 'failure' THEN 1 ELSE 0 END) AS failureCount,
                SUM(CASE WHEN event_type = 'rate_limited' THEN 1 ELSE 0 END) AS rateLimitedCount,
                SUM(CASE WHEN event_type = 'permit_busy' THEN 1 ELSE 0 END) AS permitBusyCount,
                SUM(CASE WHEN event_type = 'circuit_open' THEN 1 ELSE 0 END) AS circuitOpenCount,
                SUM(CASE WHEN event_type = 'slow_response' THEN 1 ELSE 0 END) AS slowResponseCount,
                ROUND(AVG(CASE WHEN duration_ms IS NOT NULL THEN duration_ms ELSE NULL END)) AS avgDurationMs,
                MAX(CASE WHEN event_type IN ('success', 'slow_response') THEN occurred_at ELSE NULL END) AS lastSuccessAt,
                MAX(CASE WHEN event_type IN ('failure', 'rate_limited', 'permit_busy', 'circuit_open') THEN occurred_at ELSE NULL END) AS lastFailureAt
            FROM ai_platform_health_event
            WHERE platform_code IN
            <foreach collection="platformCodes" item="code" open="(" separator="," close=")">
              #{code}
            </foreach>
              AND occurred_at >= #{startAt}
              AND occurred_at &lt; #{endAtExclusive}
            GROUP BY platform_code
            </script>
            """)
    List<PlatformHealthAggregateRow> aggregateByPlatform(@Param("platformCodes") List<String> platformCodes,
                                                         @Param("startAt") LocalDateTime startAt,
                                                         @Param("endAtExclusive") LocalDateTime endAtExclusive);
}
