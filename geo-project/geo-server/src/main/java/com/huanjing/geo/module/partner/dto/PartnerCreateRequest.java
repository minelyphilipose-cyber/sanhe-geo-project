package com.huanjing.geo.module.partner.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerCreateRequest {
    @NotBlank
    private String partnerCode;
    @NotBlank
    private String partnerName;
    @NotBlank
    private String partnerLevel;
    @DecimalMin("0.0001")
    @DecimalMax("1.0000")
    private BigDecimal discountRate;
    @DecimalMin("0.00")
    private BigDecimal initialAmount;
    private String contactName;
    private String contactPhone;
    private String city;
    private String remark;
}
