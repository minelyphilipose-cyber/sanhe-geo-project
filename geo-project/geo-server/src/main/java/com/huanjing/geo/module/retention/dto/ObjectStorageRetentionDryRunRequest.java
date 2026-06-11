package com.huanjing.geo.module.retention.dto;

import lombok.Data;

@Data
public class ObjectStorageRetentionDryRunRequest {
    private String prefix;
    private Integer safetyAgeHours;
    private Integer limitPerPrefix;
}
