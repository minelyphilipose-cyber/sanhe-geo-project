package com.huanjing.geo.module.workbench.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkbenchTodoVO {
    private Long id;
    private String sourceType;
    private String alertType;
    private String severity;
    private String message;
    private String customerName;
    private String brandName;
    private String route;
    private LocalDateTime createdAt;
}
