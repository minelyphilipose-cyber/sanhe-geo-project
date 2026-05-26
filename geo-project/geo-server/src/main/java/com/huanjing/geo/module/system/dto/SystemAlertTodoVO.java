package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemAlertTodoVO {
    private Long id;
    private String alertType;
    private String severity;
    private String source;
    private String message;
    private String contextJson;
    private LocalDateTime createdAt;
}
