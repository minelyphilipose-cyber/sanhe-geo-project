package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ObjectStorageOrphanCandidateVO {
    private String objectKey;
    private String prefix;
    private Long sizeBytes;
    private OffsetDateTime lastModified;
    private Boolean olderThanSafetyWindow;
    private Integer liveReferenceCount;
}
