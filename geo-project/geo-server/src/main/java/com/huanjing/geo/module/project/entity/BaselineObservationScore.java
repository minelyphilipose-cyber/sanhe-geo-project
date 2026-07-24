package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_observation_score")
public class BaselineObservationScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private Long observationId;
    private String algorithmVersion;
    private Boolean mentioned;
    private Boolean recommended;
    private Integer rankingPosition;
    private String sentiment;
    private String impressionState;
    private String mentionType;
    private String judgeEvidence;
    private LocalDateTime createdAt;
}
