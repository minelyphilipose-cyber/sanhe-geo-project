package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_question_batch_log")
public class GeoQuestionBatchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String eventCode;
    private String message;
    private LocalDateTime createdAt;
}
