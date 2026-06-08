package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresaleLexiconBucketCreateRequest {
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "bucketCode only supports uppercase letters, numbers and underscore")
    private String bucketCode;

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
