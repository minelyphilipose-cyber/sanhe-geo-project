package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompanyPackageBindRequest {
    @NotNull
    private Long packagePlanId;
}
