package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeywordGroupVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private String name;
    private String type;
    private String remark;
    private KeywordGroupColumnsVO columns;
    private Long estimatedKeywordCount;
    private Long savedKeywordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
