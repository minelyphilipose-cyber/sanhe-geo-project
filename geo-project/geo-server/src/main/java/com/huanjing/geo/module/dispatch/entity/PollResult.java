package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("poll_results")
public class PollResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long dispatchTaskId;
    private Long projectId;
    private Long questionId;
    private Long keywordResultId;
    private String keywordTextSnapshot;
    private Long platformId;
    private String platformCode;
    private LocalDate batchDate;
    private Integer batchNo;
    private String status;
    private Integer requestCount;
    private Long responseTimeMs;
    private Boolean isHit;
    private String matchType;
    private Boolean siteMentioned;
    private Boolean contactMentioned;
    private String recordType;
    private String detailJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
