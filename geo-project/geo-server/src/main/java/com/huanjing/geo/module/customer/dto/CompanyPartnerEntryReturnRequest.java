package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyPartnerEntryReturnRequest {

    @Size(max = 500, message = "退回原因不能超过500个字符")
    private String reason;
}
