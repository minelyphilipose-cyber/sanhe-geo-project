package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeywordPreviewVO {
    private long totalEstimated;
    private int totalAvailable;
    private int totalGenerated;
    private List<String> keywords;
}
