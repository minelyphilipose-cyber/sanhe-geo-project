package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PresaleBenchmarkSaveRequest {
    @NotBlank private String industry;
    @NotBlank private String industryRole;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal avgOverall;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal avgMention;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal avgRanking;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal avgSentiment;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal avgCoverage;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top1Overall;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top1Mention;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top1Ranking;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top1Sentiment;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top1Coverage;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal top10Score;
    @NotBlank private String confidenceLevel;
    @NotNull private Integer sampleSize;
    @NotNull private LocalDate effectiveFrom;
    @NotNull private Boolean enabled;
    private String remark;
}
