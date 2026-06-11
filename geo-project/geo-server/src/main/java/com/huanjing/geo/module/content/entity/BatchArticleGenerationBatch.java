package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("batch_article_generation_batch")
public class BatchArticleGenerationBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private String medicalIndustryCode;
    private String medicalChannelTier;
    private String topicSource;
    private String topic;
    private String topicAsQuestion;
    private Long keywordGroupId;
    private String keywordGroupName;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer warningCount;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}
