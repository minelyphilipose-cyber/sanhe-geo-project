package com.huanjing.geo.common.llm.measurement;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LlmCallObservationMapper extends BaseMapper<LlmCallObservation> {
    @Select("""
            SELECT id,
                   feature,
                   platform_code AS platformCode,
                   status,
                   error_category AS errorCategory,
                   wait_ms AS waitMs,
                   http_ms AS httpMs,
                   total_ms AS totalMs,
                   occurred_at AS occurredAt
            FROM llm_call_observation
            WHERE platform_code = #{platformCode}
              AND feature = #{feature}
              AND occurred_at >= #{since}
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<LlmCallObservation> selectRecentForFeature(@Param("platformCode") String platformCode,
                                                    @Param("feature") String feature,
                                                    @Param("since") LocalDateTime since,
                                                    @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              platform_code AS platformCode,
              COUNT(1) AS totalCount,
              SUM(CASE
                    WHEN error_category IN ('PERMIT_BUSY', 'INTERNAL_RATE_LIMITED', 'PLATFORM_429') THEN 1
                    ELSE 0
                  END) AS limitedCount
            FROM llm_call_observation
            WHERE occurred_at &gt;= #{since}
              AND platform_code IN
              <foreach collection="platformCodes" item="code" open="(" separator="," close=")">
                #{code}
              </foreach>
            GROUP BY platform_code
            </script>
            """)
    List<LlmPlatformLimitRatioRow> aggregateLimitRatio(@Param("since") LocalDateTime since,
                                                       @Param("platformCodes") List<String> platformCodes);
}
