package com.huanjing.geo.module.extension.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExtensionTaskListRow {
    private Long taskId;
    private String platform;
    private String status;
    private String publishUrl;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime fillTokenIssuedAt;
    private Long operatorId;
    private Long brandId;
    private Long selfMediaAccountId;
    private Long browserEnvironmentId;
    private Long browserEnvironmentAccountId;
    private String environmentKey;
    private String environmentProvider;
    private String providerProfileId;
}
