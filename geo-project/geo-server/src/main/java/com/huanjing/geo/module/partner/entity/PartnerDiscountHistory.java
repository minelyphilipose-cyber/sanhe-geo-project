package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("partner_discount_history")
public class PartnerDiscountHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long partnerId;
    private BigDecimal oldDiscountRate;
    private BigDecimal newDiscountRate;
    private Long operatorUserId;
    private String reason;
    private LocalDateTime createdAt;
}
