package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.util.List;

@Data
public class PublishQuotaVO {
    private String month;
    private Integer monthUsed;
    private Integer monthLimit;
    private Integer weekUsed;
    private Integer weekLimit;
    private List<String> allowedSiteTiers;
}
