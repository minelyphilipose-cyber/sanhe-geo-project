package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubjectBrandLastSelectedRow {
    private Long subjectBrandId;
    private Long lastSelectedTaskId;
    private LocalDateTime lastSelectedAt;
}
