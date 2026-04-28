package com.huanjing.geo.module.system.dto;

import lombok.Data;

@Data
public class KeywordAffixWordOptionItemVO {
    private Long id;
    private String wordText;
    private String subCategory;
    private String visualTag;
    private String industryTag;
    private Boolean isManual;
    private Boolean isTemporary;
    private String scopeType;
    private Long scopeId;
    private Integer sortOrder;
}
