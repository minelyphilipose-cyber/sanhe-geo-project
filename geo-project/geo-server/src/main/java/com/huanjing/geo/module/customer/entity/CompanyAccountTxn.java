package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("company_account_txn")
public class CompanyAccountTxn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long accountId;
    private String txnNo;
    private String txnType;
    private String bizType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long relatedProjectId;
    private Long operatorUserId;
    private String offlineReference;
    private String reason;
    private String remark;
    private LocalDateTime createdAt;
}
