package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_metric_snapshot")
public class BaselineMetricSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private String canonicalSchemaVersion;
    private String scoreAlgorithmVersion;
    private String highlightAlgorithmVersion;
    private String competitorNormalizationVersion;
    private String canonicalAggregateVersion;
    private String canonicalJson;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}
