package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_review_log")
public class ArticleReviewLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String action;
    private String comment;
    private Boolean riskOverridden;
    private Long operatorId;
    private LocalDateTime createdAt;
}

