package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ObjectStorageMigrationItemVO {
    private String objectKey;
    private String expectedChecksum;
    private Integer checksumVariantCount;
    private Integer referenceCount;
    private Long sourceSizeBytes;
    private String action;
    private String result;
    private String warningMessage;
    private String errorMessage;
    private Map<String, Object> metrics;
}
