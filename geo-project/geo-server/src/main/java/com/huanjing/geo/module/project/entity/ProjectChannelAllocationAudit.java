package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_channel_allocation_audit")
public class ProjectChannelAllocationAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private LocalDateTime operateAt;
    private Long projectId;
    private Long companyId;
    private String channelCode;
    private Integer beforeValue;
    private Integer afterValue;
    private String sourceAction;
    private LocalDateTime createdAt;
}
