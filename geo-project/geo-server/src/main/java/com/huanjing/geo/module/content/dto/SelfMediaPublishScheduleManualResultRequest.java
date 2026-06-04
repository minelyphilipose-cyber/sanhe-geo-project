package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class SelfMediaPublishScheduleManualResultRequest {
    private String platformPublishedUrl;
    private String failureCode;
    private String failureMessage;
}
