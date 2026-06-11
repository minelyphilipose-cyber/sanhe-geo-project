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
    private String targetChannel;
    private String periodType;
    private String periodKey;
    private Integer generationSlotNo;
    private String articleType;
    private String contentStyle;
    private String channelGroupCode;
    private String channelSubCode;
    private String agentSiteModule;
    private String articleTypeCode;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String perspectiveCode;
    private String allocationMode;
    private String templateSource;
    private String complianceStatus;
    private String publishReviewStatus;
    private String medicalAdReviewNo;
    private String medicalChannelTier;
    private String medicalIndustryCode;
    private String medicalCategoryCode;
    private String topic;
    private String topicAsQuestion;
    private String title;
    private String coverImageUrl;
    private String tagsJson;
    private String category;
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
    @TableField(exist = false)
    private Boolean systemGenerated;
    @TableField(exist = false)
    private String generationMode;
    @TableField(exist = false)
    private String generatedBy;
    @TableField(exist = false)
    private String promptTemplateName;
}
