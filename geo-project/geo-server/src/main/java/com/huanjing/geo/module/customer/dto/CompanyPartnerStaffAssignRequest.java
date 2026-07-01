package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyPartnerStaffAssignRequest {
    private Long staffUserId;

    @Size(max = 500)
    private String reason;
}
