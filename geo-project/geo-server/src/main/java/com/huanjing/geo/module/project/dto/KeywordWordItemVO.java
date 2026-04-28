package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class KeywordWordItemVO {
    private Long id;
    private String wordText;
    private String source;
    private Integer sortOrder;
    private Boolean isManual;
    private Boolean isTemporary;
    private String scopeType;
    private Long scopeId;
}
