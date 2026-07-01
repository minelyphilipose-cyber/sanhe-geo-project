package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerUpdateRequest {
    @NotBlank
    private String partnerName;
    @NotBlank
    private String partnerLevel;
    @DecimalMin("0.0001")
    @DecimalMax("1.0000")
    private BigDecimal discountRate;
    @Min(0)
    private Integer presaleReportFreeQuotaLimit;
    @DecimalMin("0.00")
    private BigDecimal presaleReportExtraPoints;
    @NotBlank
    private String status;
    private String contactName;
    private String contactPhone;
    private String city;
    private String remark;
}
