package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.util.List;

@Data
public class DouyinImageTextQuickPublishRequest {
    private String title;
    private String description;
    private List<Long> imageMaterialIds;
}
