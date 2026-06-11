package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ObjectStorageRetentionDryRunResponse {
    private Long retentionRunId;
    private Boolean dryRun = true;
    private Integer safetyAgeHours;
    private Integer limitPerPrefix;
    private Integer scannedObjects = 0;
    private Integer candidateCount = 0;
    private Long candidateBytes = 0L;
    private List<String> scannedPrefixes = new ArrayList<>();
    private List<ObjectStorageOrphanCandidateVO> candidates = new ArrayList<>();
    private List<ObjectStorageReferenceColumnVO> referenceColumns = new ArrayList<>();
}
