package com.huanjing.geo.module.content.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class SelfMediaPublishAutoScheduleItemVO {
    private Long articleId;
    private Long selfMediaAccountId;
    private String platform;
    private LocalDate calendarDate;
    private LocalDateTime plannedPublishAt;
    private String windowName;
    private LocalTime windowStart;
    private LocalTime windowEnd;
    private Integer dayType;
    private String dayName;
    private Integer week;
    private Boolean adjustedWorkday;
    private String status;
    private String rejectionCode;
    private String rejectionMessage;
}
