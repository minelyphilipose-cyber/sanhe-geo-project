package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("company_channel_quota_usage")
public class CompanyChannelQuotaUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private String channelCode;
    private String periodType;
    private String periodKey;
    private Integer quotaLimit;
    private Integer usedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
