package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_publish_schedule_request")
public class SelfMediaPublishScheduleRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long operatorId;
    private String requestIdempotencyKey;
    private String normalizedRequestHash;
    private String requestPayload;
    private String status;
    private Integer scheduleCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
