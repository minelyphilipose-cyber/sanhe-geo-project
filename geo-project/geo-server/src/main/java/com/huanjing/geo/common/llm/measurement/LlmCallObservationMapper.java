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
