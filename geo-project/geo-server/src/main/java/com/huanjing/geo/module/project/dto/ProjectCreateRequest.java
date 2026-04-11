package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectCreateRequest {
    @NotNull
    private Long brandId;
    @NotBlank
    private String projectName;
    @NotBlank
    private String packageType;
    @NotNull
    private Long packagePrice;
    @NotNull
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
