package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("partner_account_txn")
public class PartnerAccountTxn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partnerId;
    private Long accountId;
    private String txnNo;
    private String txnType;
    private String bizType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long relatedProjectId;
    private Long relatedCompanyId;
    private Long relatedStartRequestId;
    private Long relatedPresaleReportId;
    private String packageSnapshotJson;
    private Long rechargeOrderId;
    private Long operatorUserId;
    private String offlineReference;
    private String remark;
    private LocalDateTime createdAt;
}
