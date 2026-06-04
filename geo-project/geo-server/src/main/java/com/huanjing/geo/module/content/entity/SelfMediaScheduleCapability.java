package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_schedule_capability")
public class SelfMediaScheduleCapability {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String verificationStatus;
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
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
