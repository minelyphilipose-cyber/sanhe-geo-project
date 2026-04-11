package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectUpdateRequest {
    @NotBlank
    private String projectName;
    @NotBlank
    private String packageType;
    private Long packagePrice;
    private Integer serviceMonths;
    @NotBlank
    private String ownerType;
    private Long partnerId;
    private String deliveryMode;
    private LocalDateTime signedAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryGoal;
    private String remark;
}
