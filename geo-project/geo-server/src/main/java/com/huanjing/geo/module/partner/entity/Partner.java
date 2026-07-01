package com.huanjing.geo.module.partner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("partner")
public class Partner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String partnerCode;
    private String partnerName;
    private String partnerLevel;
    private BigDecimal discountRate;
    private Integer presaleReportFreeQuotaLimit;
    private BigDecimal presaleReportExtraPoints;
    private String status;
    private String contactName;
    private String contactPhone;
    private String city;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
