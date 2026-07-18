package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_citations")
public class PollCitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long attemptId;
    private Long sourceId;
    private Integer citationIndex;
    private Integer answerStart;
    private Integer answerEnd;
    private String citationText;
    private String confidence;
    private String validationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
