package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("douyin_article_image_batch")
public class DouyinArticleImageBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer sourceVersionNo;
    private Integer appliedVersionNo;
    private Long subjectBrandId;
    private Long modelConfigId;
    private String requestKey;
    private String status;
    private String runToken;
    private Integer imageCount;
    private String styleCode;
    private String sourceTitle;
    private String sourceMarkdown;
    private String brandFactsJson;
    private String planJson;
    private String materialIdsJson;
    private String errorMessage;
    private Boolean automatic;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
