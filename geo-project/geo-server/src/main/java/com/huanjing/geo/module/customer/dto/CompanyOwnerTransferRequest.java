package com.huanjing.geo.module.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyOwnerTransferRequest {
    @NotNull
    private Long newOwnerId;

    @Size(max = 500)
    private String reason;
}
