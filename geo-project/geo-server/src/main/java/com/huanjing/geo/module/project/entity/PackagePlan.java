package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huanjing.geo.module.content.entity.PackageContentConfig;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("package_plan")
public class PackagePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageType;
    private String packageName;
    private BigDecimal standardPrice;
    private Integer serviceMonths;
    private Integer questionPoolSize;
    private Integer coreQuestionCount;
    private Integer platformP0Count;
    private Integer platformP1Count;
    private Integer platformP2Count;
    private Integer perQuestionPlatformCalls;
    private Integer perQuestionCallsP0;
    private Integer perQuestionCallsP1;
    private Integer perQuestionCallsP2;
    private Integer biweeklyFrequency;
    private String monthlyReportDepth;
    private String quarterlyReportDepth;
    private String consultantIntensity;
    private String competitorInsightDepth;
    private String mediaDistributionIntensity;
    private String commitmentTargetIntensity;
    private String targetMetricType;
    private BigDecimal targetMetricValue;
    private Integer targetWindowDays;
    private Boolean enabled;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<PackageContentConfig> contentConfigs;
}
