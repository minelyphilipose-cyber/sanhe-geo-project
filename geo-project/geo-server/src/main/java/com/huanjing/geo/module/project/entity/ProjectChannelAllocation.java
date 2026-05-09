package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_channel_allocation")
public class ProjectChannelAllocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private String channelCode;
    private String periodTypeSnapshot;
    private Integer packageQuotaLimitSnapshot;
    private Integer allocatedCount;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
