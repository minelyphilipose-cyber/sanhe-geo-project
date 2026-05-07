package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private Long companyId;
    private String companyName;
    private Long brandId;
    private String brandName;
    private String projectName;
    private String projectAliases;
    @TableField(exist = false)
    private String packageType;
    @TableField(exist = false)
    private BigDecimal packagePrice;
    @TableField(exist = false)
    private Integer serviceMonths;
    private Integer planQuestionPoolSize;
    private Integer planCoreQuestionCount;
    private String planMonthlyReportDepth;
    private String planQuarterlyReportDepth;
    private String planConsultantIntensity;
    private String planCompetitorInsightDepth;
    private String planMediaDistributionIntensity;
    private String planCommitmentTargetIntensity;
    private String planTargetMetricType;
    private BigDecimal planTargetMetricValue;
    private Integer planTargetWindowDays;
    private String status;
    private String stage;
    private String ownerType;
    private String sourceType;
    private Boolean contentGenerationEnabled;
    private Long partnerId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String targetRegions;
    private String targetAudience;
    private String customStatement;
    private String contentTone;
    private String preferredAngles;
    private String extraForbiddenPhrases;
    private String contentNote;
    private BigDecimal discountRateSnapshot;
    private BigDecimal deductionAmount;
    private String deductionTxnNo;
    private String deliveryMode;
    private LocalDateTime signedAt;
    private LocalDateTime activatedAt;
    private LocalDate biweeklyAnchorDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime expiredAt;
    private String primaryGoal;
    private Long createdBy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    @TableField(exist = false)
    private List<String> selectedPlatformCodesP0;
    @TableField(exist = false)
    private List<String> selectedPlatformCodesP1;
    @TableField(exist = false)
    private List<String> selectedPlatformCodesP2;
    @TableField(exist = false)
    private List<Long> selectedKeywordGroupIds;
    @TableField(exist = false)
    private Integer selectedKeywordGroupCount;
    @TableField(exist = false)
    private Long selectedKeywordSavedKeywords;
}
