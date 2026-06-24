package com.huanjing.geo.common.llm.measurement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_capacity_minute_metric")
public class LlmCapacityMinuteMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private LocalDateTime bucketMinute;
    private String platformCode;
    private String feature;
    private String governanceStack;
    private Long globalActivePeak;
    private Long featureActivePeak;
    private Long platformActivePeak;
    private Long permitBusyCount;
    private Long permitWaiterPeak;
    private Long internalRateLimitedCount;
    private Long platform429Count;
    private Long http5xxCount;
    private Long timeoutCount;
    private Long legacyRateLimitedCount;
    private Long legacyConcurrencyWaiterPeak;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
