package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("company_channel_quota_ledger")
public class CompanyChannelQuotaLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long projectId;
    private String channelCode;
    private String periodType;
    private String periodKey;
    private Integer deltaCount;
    private String status;
    private String bizType;
    private String bizId;
    private LocalDateTime reservedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime expireCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
