package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ArticleDistributeRequest {
    @NotNull
    private Long siteId;
    @Min(1)
    private Integer fid;
}
