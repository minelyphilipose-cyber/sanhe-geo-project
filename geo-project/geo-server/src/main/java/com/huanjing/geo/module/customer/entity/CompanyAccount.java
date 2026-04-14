package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("company_account")
public class CompanyAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private BigDecimal currentBalance;
    private BigDecimal totalRecharge;
    private BigDecimal totalDeduction;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
