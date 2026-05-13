package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PresalePage03MarketConfigUpdateRequest {
    @NotBlank
    @Size(max = 32)
    private String marketLabel;
    @NotBlank
    @Size(max = 32)
    private String marketSource;
    @NotBlank
    @Size(max = 12)
    private String appMonthlyActiveValue;
    @NotBlank
    @Size(max = 8)
    private String appMonthlyActiveUnit;
    @NotBlank
    @Size(max = 12)
    private String dailyActiveUsersValue;
    @NotBlank
    @Size(max = 8)
    private String dailyActiveUsersUnit;
    @NotBlank
    @Size(max = 12)
    private String dailyQuestionTotalValue;
    @NotBlank
    @Size(max = 8)
    private String dailyQuestionTotalUnit;
    @NotBlank
    @Size(max = 12)
    private String doubaoMonthlyUsageValue;
    @NotBlank
    @Size(max = 8)
    private String doubaoMonthlyUsageUnit;
    @NotBlank
    @Size(max = 12)
    private String platform1Name;
    @NotBlank
    @Size(max = 12)
    private String platform1Value;
    @NotBlank
    @Size(max = 12)
    private String platform2Name;
    @NotBlank
    @Size(max = 12)
    private String platform2Value;
    @NotBlank
    @Size(max = 12)
    private String platform3Name;
    @NotBlank
    @Size(max = 12)
    private String platform3Value;
    @NotBlank
    @Size(max = 18)
    private String platformSuffix;
    @NotBlank
    @Size(max = 30)
    private String page03DataSource;
    @NotBlank
    @Size(max = 150)
    private String footnote;
    @NotNull
    @Min(3)
    @Max(3)
    private Integer questionCount;
}
