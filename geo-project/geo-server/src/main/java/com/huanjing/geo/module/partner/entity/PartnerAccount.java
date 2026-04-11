package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("partner_account")
public class PartnerAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partnerId;
    private Long currentBalance;
    private Long totalRecharge;
    private Long totalDeduction;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
