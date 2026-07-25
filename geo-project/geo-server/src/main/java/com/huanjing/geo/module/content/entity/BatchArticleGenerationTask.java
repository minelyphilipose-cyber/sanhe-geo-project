package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    private Long sourceBrandId;
    private Long subjectBrandId;
    private Long subjectProjectId;
    private Long articleId;
    private Integer rowNo;
    private Integer articleIndexInRow;
    private Integer articleIndexInBatch;
    private String articleType;
    private String tone;
    private String contentStyle;
    private String channelGroupCode;
    private String channelSubCode;
    private String agentSiteModule;
    private String articleTypeCode;
    private String questionSceneCode;
    private String medicalIndustryCode;
    private String medicalCategoryCode;
    private String medicalCategoryName;
    private Long topicAngleId;
    private String structureSkeleton;
    private String focus;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String perspectiveCode;
    private String perspectiveMatchedScope;
    private Long perspectiveMatchedConfigId;
    private String allocationMode;
    private String templateSource;
    private String suggestedPlatformCodes;
    private String selectedPlatformCodes;
    private Boolean readinessWarningConfirmed;
    private String readinessWarningCodes;
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
    private String complianceStatus;
    private String complianceIssuesJson;
    private Long discardedArticleId;
    private String promptSnapshot;
    private String inputSnapshot;
    private String responseSnapshot;
    private LocalDateTime snapshotPurgedAt;
    private String modelPlatformCode;
    private String modelId;
    private String errorMessage;
    private Integer retryCount;
    private Integer infrastructureRetryCount;
    private Integer complianceRetryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(exist = false)
    private String promptTemplateName;
}
