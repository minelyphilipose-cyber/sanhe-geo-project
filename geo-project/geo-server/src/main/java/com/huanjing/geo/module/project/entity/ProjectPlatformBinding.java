package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_platform_binding")
public class ProjectPlatformBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String platformCode;
    private String platformName;
    private String priorityLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

