package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("batch_article_generation_task")
public class BatchArticleGenerationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long articleId;
    private Integer rowNo;
    private Integer articleIndexInRow;
    private Integer articleIndexInBatch;
    private String articleType;
    private String tone;
    private String contentStyle;
    private String length;
    private String topic;
    private String topicAsQuestion;
    private Long keywordGroupId;
    private String keywordGroupName;
    private String contentAngle;
    private String audiencePerspective;
    private String extraPrompt;
    private String status;
    private String qualityStatus;
    private String qualityIssuesJson;
    private String promptSnapshot;
    private String inputSnapshot;
    private String responseSnapshot;
    private String modelPlatformCode;
    private String modelId;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
