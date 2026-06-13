package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ObjectStorageMigrationResponse {
    private Long retentionRunId;
    private Boolean dryRun;
    private Integer limit;
    private String cursorObjectKey;
    private String prefix;
    private Boolean hasMore = false;
    private String nextCursorObjectKey;
    private Integer candidateCount = 0;
    private Integer migratedCount = 0;
    private Integer skippedCount = 0;
    private Integer failedCount = 0;
    private Integer warningCount = 0;
    private Long estimatedBytes = 0L;
    private Long migratedBytes = 0L;
    private List<ObjectStorageMigrationItemVO> items = new ArrayList<>();
}
