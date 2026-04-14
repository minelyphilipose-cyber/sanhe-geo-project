package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_pool_item")
public class QuestionPoolItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Long projectId;
    private String questionText;
    private String questionType;
    private String priority;
    private Boolean isCore;
    private String contentStrategy;
    private String strategyKeywords;
    private String strategySuggestedType;
    private LocalDateTime strategyGeneratedAt;
    private String strategyStatus;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
