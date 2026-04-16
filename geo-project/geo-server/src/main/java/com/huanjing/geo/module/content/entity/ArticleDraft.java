package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("article_draft")
public class ArticleDraft {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private String articleType;
    private String title;
    private String status;
    private Boolean hasRisk;
    private String riskSeverity;
    private String riskWordsJson;
    private Boolean isDuplicateTitle;
    private BigDecimal duplicateScore;
    private Long duplicateArticleId;
    private Integer currentVersionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    @TableField(exist = false)
    private String projectName;
}
