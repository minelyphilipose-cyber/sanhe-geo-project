package com.huanjing.geo.module.presale.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class DataRetentionSlimDryRunResponse {
    private Long retentionRunId;
    private String domain;
    private Boolean dryRun = true;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer limitPerDomain;
    private Integer candidateCount = 0;
    private Integer eligibleCount = 0;
    private Integer blockedCount = 0;
    private Integer warningCount = 0;
    private List<DataRetentionSlimItemVO> items = new ArrayList<>();
}
