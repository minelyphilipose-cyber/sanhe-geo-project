package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_collection_task")
public class BaselineCollectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long projectId;
    private String status;
    private String selectedPlatformCodesJson;
    private Integer samplePerCell;
    private Integer questionCount;
    private Integer platformCount;
    private Integer totalObservationCount;
    private Integer successObservationCount;
    private Integer failedObservationCount;
    private Integer scoreCount;
    private Integer competitorMentionCount;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
