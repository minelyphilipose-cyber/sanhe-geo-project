package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ArticleArchiveDryRunItemVO {
    private Long articleId;
    private Long versionId;
    private Long projectId;
    private Integer versionNo;
    private Boolean currentVersion;
    private String articleStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime contentArchivedAt;
    private LocalDateTime contentPurgedAt;
    private Boolean eligible;
    private List<String> blockedReasons = new ArrayList<>();
    private String action;
    private String result;
    private String errorMessage;
    private String plannedObjectKey;
    private String contentChecksum;
    private Long contentBytes;
    private Integer publishRecordCount;
    private Integer activeDistributionTaskCount;
    private Integer activeSelfMediaScheduleCount;
    private Map<String, Object> metrics;
}
