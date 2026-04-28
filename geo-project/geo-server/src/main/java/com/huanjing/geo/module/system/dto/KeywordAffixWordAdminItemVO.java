package com.huanjing.geo.module.system.dto;

import lombok.Data;

@Data
public class KeywordAffixWordAdminItemVO {
    private Long id;
    private String type;
    private String affixKind;
    private String wordText;
    private String subCategory;
    private String visualTag;
    private String industryTag;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean isManual;
    private Boolean isTemporary;
    private String scopeType;
    private Long scopeId;
    private String approvalStatus;
}
