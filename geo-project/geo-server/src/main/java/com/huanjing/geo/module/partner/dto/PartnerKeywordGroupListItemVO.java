package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartnerKeywordGroupListItemVO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long projectId;
    private String projectName;
    private String packageType;
    private String name;
    private String typeLabel;
    private Long savedCoreQuestionCount;
    private LocalDateTime updatedAt;
}
