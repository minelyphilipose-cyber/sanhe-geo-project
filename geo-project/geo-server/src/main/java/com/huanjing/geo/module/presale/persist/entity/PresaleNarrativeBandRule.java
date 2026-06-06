package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("presale_narrative_band_rule")
public class PresaleNarrativeBandRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configVersion;
    private String band;
    private BigDecimal minOverall;
    private BigDecimal maxOverall;
    private BigDecimal minAvgRatio;
    private BigDecimal maxAvgRatio;
    private BigDecimal minTop1Ratio;
    private BigDecimal maxTop1Ratio;
    private BigDecimal minDeltaAvg;
    private BigDecimal maxDeltaAvg;
    private BigDecimal minDeltaTop1;
    private BigDecimal maxDeltaTop1;
    private BigDecimal minMentionScore;
    private BigDecimal minCoverageScore;
    private Boolean enabled;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
