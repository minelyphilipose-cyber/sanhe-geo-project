package com.huanjing.geo.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_question_items")
public class PresaleQuestionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long setId;
    private Long projectId;
    private String content;
    private String questionType;
    private String source;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
