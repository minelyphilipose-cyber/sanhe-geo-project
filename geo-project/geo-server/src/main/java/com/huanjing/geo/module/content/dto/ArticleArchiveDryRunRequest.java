package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ArticleArchiveDryRunRequest {
    private Boolean dryRun = true;
    private Long projectId;
    private LocalDate publishedStartDate;
    private LocalDate publishedEndDate;
    private Integer minPublishedAgeDays;
    private Integer limit;
}
