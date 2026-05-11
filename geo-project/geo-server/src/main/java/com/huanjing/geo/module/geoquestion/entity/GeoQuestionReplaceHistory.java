package com.huanjing.geo.module.geoquestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("geo_question_replace_history")
public class GeoQuestionReplaceHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private String oldQuestionText;
    private String newQuestionText;
    private String reason;
    private LocalDateTime createdAt;
}
