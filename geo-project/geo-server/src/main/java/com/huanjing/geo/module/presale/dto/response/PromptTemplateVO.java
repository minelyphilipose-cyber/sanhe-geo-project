package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromptTemplateVO {
    private Long id;
    private String promptCode;
    private String category;
    private String businessValue;
    private String promptContent;
    private Boolean hasCompetitorVar;
    private Integer sortOrder;
    private String remark;
    private String templateVersion;
}
