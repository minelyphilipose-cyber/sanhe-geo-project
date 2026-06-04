package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_publish_schedule_environment_lock")
public class SelfMediaPublishScheduleEnvironmentLock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long browserEnvironmentId;
    private Long scheduleId;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
