package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

@Data
public class ManualQuestionPollPlatformOption {
    private Long platformId;
    private String platformCode;
    private String channelCode;
    private String platformName;
    private String integrationType;
    private String modelId;
    private Boolean enabled;
    private Boolean enabledForQuestionPoll;
    private boolean selectable;
    private String unavailableReason;
}
