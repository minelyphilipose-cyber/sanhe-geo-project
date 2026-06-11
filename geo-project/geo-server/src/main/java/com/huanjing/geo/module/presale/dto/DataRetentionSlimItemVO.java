package com.huanjing.geo.module.presale.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DataRetentionSlimItemVO {
    private String domain;
    private String tableName;
    private Long sourceId;
    private Long parentId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private Boolean eligible;
    private List<String> blockedReasons = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private Map<String, Object> metrics;
}
