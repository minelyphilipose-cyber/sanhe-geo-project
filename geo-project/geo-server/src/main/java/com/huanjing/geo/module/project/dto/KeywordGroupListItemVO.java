package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeywordGroupListItemVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long projectId;
    private String projectName;
    private String packageType;
    private String name;
    private String type;
    private String typeLabel;
    private Boolean legacyType;
    private Long savedKeywordCount;
    private LocalDateTime updatedAt;
}
