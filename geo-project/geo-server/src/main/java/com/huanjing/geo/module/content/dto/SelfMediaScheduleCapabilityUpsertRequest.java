package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelfMediaScheduleCapabilityUpsertRequest {
    private String platform;

    @NotBlank
    private String verificationStatus;

    @NotNull
    private Boolean supportsSchedule;

    private Integer minDelayMinutes;
    private Integer maxDelayMinutes;
    private Boolean saveCreatesSchedule;
    private Boolean supportsCancel;
    private Boolean supportsModify;
    private Boolean supportsPublishCheck;
    private String v1Strategy;
    private String selectorStatus;
    private String evidenceJson;
    private String notes;
}
