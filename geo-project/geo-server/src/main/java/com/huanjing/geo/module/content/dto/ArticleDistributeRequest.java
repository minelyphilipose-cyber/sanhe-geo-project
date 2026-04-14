package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleDistributeRequest {
    @NotNull
    private Long siteId;
}
