package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendedSitesResponseVO {
    private Boolean fallbackToGeneral;
    private List<RecommendedSiteVO> sites;
}
