package com.huanjing.geo.module.presale.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DataRetentionSlimDryRunRequest {
    private String domain;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer limitPerDomain;
}
