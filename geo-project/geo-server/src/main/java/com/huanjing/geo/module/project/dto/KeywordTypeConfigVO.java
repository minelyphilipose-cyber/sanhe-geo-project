package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class KeywordTypeConfigVO {
    private String type;
    private String label;
    private String description;
    private String structure;
    private boolean areaEnabledByDefault;
    private boolean industryRequired;
    private boolean supportsManualAdd;
    private boolean functionIndustryRequired;
    private KeywordColumnVisibilityVO columns;
    private KeywordRequiredColumnsVO requiredColumns;
}
