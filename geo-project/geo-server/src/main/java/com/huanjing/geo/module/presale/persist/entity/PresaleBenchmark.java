package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("presale_benchmark")
public class PresaleBenchmark {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industry;
    private String industryRole;
    private BigDecimal avgOverall;
    private BigDecimal avgMention;
    private BigDecimal avgRanking;
    private BigDecimal avgSentiment;
    private BigDecimal avgCoverage;
    private BigDecimal top1Overall;
    private BigDecimal top1Mention;
    private BigDecimal top1Ranking;
    private BigDecimal top1Sentiment;
    private BigDecimal top1Coverage;
    private BigDecimal top10Score;
    private String confidenceLevel;
    private String source;
    private Integer sampleSize;
    private Boolean enabled;
    private LocalDate effectiveFrom;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
