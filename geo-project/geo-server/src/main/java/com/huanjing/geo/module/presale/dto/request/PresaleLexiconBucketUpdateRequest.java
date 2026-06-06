package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleLexiconBucketUpdateRequest {
    @NotBlank
    @Size(max = 100)
    private String bucketName;

    @NotBlank
    @Size(max = 50)
    private String customerTerm;

    @NotBlank
    @Size(max = 50)
    private String conversionTerm;

    @Size(max = 50)
    private String defaultIndustryShort;

    @NotNull
    private Boolean enabled;

    @Size(max = 500)
    private String remark;
}
