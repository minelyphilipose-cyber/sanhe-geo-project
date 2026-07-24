package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("baseline_question_snapshot")
public class BaselineQuestionSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private String questionKey;
    private Long sourceKeywordResultId;
    private String questionText;
    private String valueTier;
    private String sourceQuestionTier;
    private String sourcePriority;
    private String intentType;
    private String sceneCode;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
