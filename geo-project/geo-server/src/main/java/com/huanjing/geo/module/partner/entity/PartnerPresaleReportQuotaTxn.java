package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("partner_presale_report_quota_txn")
public class PartnerPresaleReportQuotaTxn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partnerId;
    private String requestId;
    private String requestHash;
    private String requestPayloadSnapshotJson;
    private Long reportId;
    private String bizType;
    private BigDecimal pointsAmount;
    private Integer quotaAmount;
    private String status;
    private String failureCode;
    private String failureMessage;
    private Long relatedPointsTxnId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime refundedAt;
}
