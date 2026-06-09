package com.huanjing.geo.module.content.vo;

import lombok.Data;

@Data
public class SelfMediaAccountPlatformOptionVO {
    private String platform;
    private String label;
    private Boolean eligible;
    private Boolean quotaEnabled;
    private Integer quotaLimit;
    private String quotaStatus;
    private Boolean scheduleReady;
    private String scheduleCode;
    private String reason;
}
