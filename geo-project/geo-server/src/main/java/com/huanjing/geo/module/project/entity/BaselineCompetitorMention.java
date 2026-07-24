package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_competitor_mention")
public class BaselineCompetitorMention {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long observationId;
    private String algorithmVersion;
    private Long competitorId;
    private String normalizedName;
    private String rawText;
    private Integer mentionCount;
    private Boolean tracked;
    private Integer startOffset;
    private Integer endOffset;
    private LocalDateTime createdAt;
}
