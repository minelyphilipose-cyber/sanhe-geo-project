package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_observation")
public class BaselineObservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long questionSnapshotId;
    private String platformCode;
    private String platformName;
    private Integer sampleSeq;
    private String callStatus;
    private String rawResponseText;
    private Integer requestCount;
    private Long responseTimeMs;
    private String errorCode;
    private String errorMessage;
    private String modelId;
    private String modelName;
    private LocalDateTime testedAt;
    private LocalDateTime createdAt;
}
