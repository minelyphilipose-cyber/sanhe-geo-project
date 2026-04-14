package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_question_rel")
public class ArticleQuestionRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long versionId;
    private Long questionId;
    private String questionText;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

