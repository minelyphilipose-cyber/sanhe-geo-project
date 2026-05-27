package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationVO;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long brandId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brandName;
    private String projectName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String projectAliases;
    @TableField(exist = false)
    private String packageType;
    @TableField(exist = false)
    private BigDecimal packagePrice;
    @TableField(exist = false)
    private Integer serviceMonths;
    private Integer planKeywordGroupLimit;
    private Integer planKeywordGroupLimitA;
    private Integer planKeywordGroupLimitB;
    private Integer planKeywordGroupLimitC;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long partnerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetRegions;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coreKeywords;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetAudience;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customStatement;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentTone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String preferredAngles;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extraForbiddenPhrases;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentNote;
    private BigDecimal discountRateSnapshot;
    private BigDecimal deductionAmount;
    private String deductionTxnNo;
    private String deliveryMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime signedAt;
    private LocalDateTime activatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private LocalDateTime expiredAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String primaryGoal;
    private Long createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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
    private List<String> customerRequirements;
    @TableField(exist = false)
    private Integer selectedKeywordGroupCount;
    @TableField(exist = false)
    private Long selectedKeywordSavedKeywords;
    @TableField(exist = false)
    private Long selectedKeywordSavedKeywordsA;
    @TableField(exist = false)
    private Long selectedKeywordSavedKeywordsB;
    @TableField(exist = false)
    private Long selectedKeywordSavedKeywordsC;
    @TableField(exist = false)
    private List<com.huanjing.geo.module.project.dto.KeywordGroupListItemVO> selectedKeywordGroups;
    @TableField(exist = false)
    private List<ProjectChannelAllocationVO> channelAllocations;
    @TableField(exist = false)
    private Long allocationVersion;
}
