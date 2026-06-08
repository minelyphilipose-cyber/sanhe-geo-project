package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_publish_schedule_alert")
public class SelfMediaPublishScheduleAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private Long brandId;
    private Long articleId;
    private Long selfMediaAccountId;
    private Long browserEnvironmentId;
    private String platform;
    private String alertType;
    private String severity;
    private String status;
    private String message;
    private String evidenceJson;
    private String activeKey;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
