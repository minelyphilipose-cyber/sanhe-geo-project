package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PendingItemVO {
    private String type;
    private String title;
    private String description;
    private String targetPath;
    private Long targetId;
    private LocalDateTime createdAt;
    private String priority;
}
