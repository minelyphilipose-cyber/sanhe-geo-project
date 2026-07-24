package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BaselineSnapshotVO {
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private Integer runSeq;
    private String status;
    private String schemaVersion;
    private String intentRubricVersion;
    private String algorithmVersionsJson;
    private String selectedVersionsJson;
    private Long sourcePollBatchId;
    private LocalDateTime sealedAt;
    private Long sealedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer questionCount;
    private List<BaselineQuestionSnapshotVO> questions;
    private Integer competitorCount;
    private List<BaselineSnapshotCompetitorSourceRequest> competitorSources;
    private List<String> warnings;
}
