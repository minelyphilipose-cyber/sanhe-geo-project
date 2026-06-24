package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EntityJudgeRunRequest {
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer limit = 50;
}
