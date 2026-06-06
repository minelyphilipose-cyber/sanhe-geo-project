package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleIndustryBucketDraftUpdateRequest {
    @NotBlank
    @Size(max = 50)
    private String bucketCode;

    @Size(max = 50)
    private String industryShort;

    @Size(max = 500)
    private String reason;

    private Boolean suggestNewBucket;
}
