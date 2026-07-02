package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SelfMediaPublishScheduleManualResultRequest {
    private String platformPublishedUrl;
    private String platformPublishId;
    private LocalDateTime platformPublishedAt;
    private String note;
    private String failureCode;
    private String failureMessage;
}
