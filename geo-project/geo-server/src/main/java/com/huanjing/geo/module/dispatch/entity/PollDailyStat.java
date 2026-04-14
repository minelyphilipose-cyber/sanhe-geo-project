package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("poll_daily_stats")
public class PollDailyStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long dispatchTaskId;
    private Long projectId;
    private String projectName;
    private Long platformId;
    private String platformCode;
    private String platformName;
    private LocalDate batchDate;
    private Integer batchNo;
    private Integer questionCount;
    private Integer requestCount;
    private Integer completedCount;
    private Integer failedCount;
    private Integer hitCount;
    private Integer siteMentionCount;
    private Integer contactMentionCount;
    private BigDecimal hitRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
