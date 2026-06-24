package com.huanjing.geo.common.llm.measurement;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface LlmCapacityMinuteMetricMapper extends BaseMapper<LlmCapacityMinuteMetric> {
    @Insert("""
            INSERT INTO llm_capacity_minute_metric (
              run_id, bucket_minute, platform_code, feature, governance_stack,
              global_active_peak, feature_active_peak, platform_active_peak,
              permit_busy_count, permit_waiter_peak, internal_rate_limited_count,
              platform429_count, http5xx_count, timeout_count,
              legacy_rate_limited_count, legacy_concurrency_waiter_peak
            ) VALUES (
              #{metric.runId}, #{metric.bucketMinute}, #{metric.platformCode}, #{metric.feature}, #{metric.governanceStack},
              #{metric.globalActivePeak}, #{metric.featureActivePeak}, #{metric.platformActivePeak},
              #{metric.permitBusyCount}, #{metric.permitWaiterPeak}, #{metric.internalRateLimitedCount},
              #{metric.platform429Count}, #{metric.http5xxCount}, #{metric.timeoutCount},
              #{metric.legacyRateLimitedCount}, #{metric.legacyConcurrencyWaiterPeak}
            )
            ON DUPLICATE KEY UPDATE
              global_active_peak = GREATEST(global_active_peak, VALUES(global_active_peak)),
              feature_active_peak = GREATEST(feature_active_peak, VALUES(feature_active_peak)),
              platform_active_peak = GREATEST(platform_active_peak, VALUES(platform_active_peak)),
              permit_busy_count = permit_busy_count + VALUES(permit_busy_count),
              permit_waiter_peak = GREATEST(permit_waiter_peak, VALUES(permit_waiter_peak)),
              internal_rate_limited_count = internal_rate_limited_count + VALUES(internal_rate_limited_count),
              platform429_count = platform429_count + VALUES(platform429_count),
              http5xx_count = http5xx_count + VALUES(http5xx_count),
              timeout_count = timeout_count + VALUES(timeout_count),
              legacy_rate_limited_count = legacy_rate_limited_count + VALUES(legacy_rate_limited_count),
              legacy_concurrency_waiter_peak = GREATEST(legacy_concurrency_waiter_peak, VALUES(legacy_concurrency_waiter_peak)),
              updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(@Param("metric") LlmCapacityMinuteMetric metric);
}
