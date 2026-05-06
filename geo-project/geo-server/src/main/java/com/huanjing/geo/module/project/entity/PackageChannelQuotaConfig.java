package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("package_channel_quota_config")
public class PackageChannelQuotaConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packagePlanId;
    private String channelCode;
    private String periodType;
    private Integer quotaLimit;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
