package com.huanjing.geo.module.retention.dto;

import lombok.Data;

@Data
public class ObjectStorageMigrationRequest {
    private Boolean dryRun = true;
    private Integer limit;
    private String cursorObjectKey;
    private String prefix;
}
