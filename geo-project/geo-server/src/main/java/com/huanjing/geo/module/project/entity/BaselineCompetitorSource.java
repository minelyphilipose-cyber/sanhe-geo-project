package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_competitor_source")
public class BaselineCompetitorSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long competitorId;
    private String competitorName;
    private String aliasesJson;
    private String sourceType;
    private String sourceUrl;
    private String sourceNote;
    private String reviewStatus;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
