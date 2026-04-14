package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_poll_rotation")
public class ProjectPollRotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String priorityLevel;
    private Integer rotationOffset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
