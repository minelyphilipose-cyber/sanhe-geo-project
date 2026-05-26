package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_platform_health_event")
public class AiPlatformHealthEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private String feature;
    private String eventType;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime occurredAt;
}
