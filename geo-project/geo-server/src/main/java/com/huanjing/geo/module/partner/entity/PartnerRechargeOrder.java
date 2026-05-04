package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("partner_recharge_order")
public class PartnerRechargeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long partnerId;
    private BigDecimal amount;
    private String status;
    private String offlineReference;
    private String applyRemark;
    private String rejectReason;
    private Long applicantUserId;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private Long accountTxnId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
