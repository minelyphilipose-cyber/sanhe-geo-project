package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_self_media_schedule_config")
public class ProjectSelfMediaScheduleConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long brandId;
    private Long companyId;
    private Boolean autoScheduleEnabled;
    private String defaultScheduleStrategy;
    private Boolean includeAdjustedWorkdays;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
