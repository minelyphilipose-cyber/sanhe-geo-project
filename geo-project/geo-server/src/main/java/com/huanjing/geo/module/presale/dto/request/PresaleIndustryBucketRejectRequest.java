package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleIndustryBucketRejectRequest {
    @Size(max = 500)
    private String reason;
}
