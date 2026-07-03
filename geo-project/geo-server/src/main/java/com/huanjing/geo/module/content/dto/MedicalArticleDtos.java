package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class MedicalArticleDtos {

    public record TopicAngleSaveRequest(
            @NotBlank @Size(max = 32) String industryCode,
            @Size(max = 64) String industryName,
            @NotBlank @Size(max = 64) String categoryCode,
            @NotBlank @Size(max = 128) String categoryName,
            @NotBlank @Size(max = 500) String topicAngle,
            @Size(max = 64) String recommendedFocus,
            Boolean enabled,
            Integer sortOrder
    ) {
    }

    public record TopicAngleVO(
            Long id,
            String industryCode,
            String industryName,
            String categoryCode,
            String categoryName,
            String topicAngle,
            String recommendedFocus,
            Boolean enabled,
            Integer sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TopicAngleCategoryVO(
            String industryCode,
            String industryName,
            String categoryCode,
            String categoryName,
            Long topicAngleCount
    ) {
    }

    public record ComplianceRuleSaveRequest(
            @NotBlank @Size(max = 64) String ruleType,
            @Size(max = 32) String industryCode,
            @Size(max = 32) String channelTier,
            @Size(max = 64) String channelGroupCode,
            @Size(max = 64) String channelSubCode,
            @NotBlank @Size(max = 500) String pattern,
            @Size(max = 32) String matchMode,
            @Size(max = 32) String severity,
            Boolean enabled,
            @Size(max = 500) String remark
    ) {
    }

    public record ComplianceRuleVO(
            Long id,
            String ruleType,
            String industryCode,
            String channelTier,
            String channelGroupCode,
            String channelSubCode,
            String pattern,
            String matchMode,
            String severity,
            Boolean enabled,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ComplianceKernelSaveRequest(
            @NotBlank @Size(max = 32) String industryCode,
            @NotBlank @Size(max = 32) String channelTier,
            @NotBlank @Size(max = 128) String kernelName,
            @NotBlank String systemPrompt,
            @NotNull Integer brandExposureLimit,
            Boolean requireManualPublishReview,
            Boolean enabled,
            Integer versionNo
    ) {
    }

    public record ComplianceKernelVO(
            Long id,
            String industryCode,
            String channelTier,
            String kernelName,
            String systemPrompt,
            Integer brandExposureLimit,
            Boolean requireManualPublishReview,
            Boolean enabled,
            Integer versionNo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ChannelStyleSaveRequest(
            @NotBlank @Size(max = 64) String channelGroupCode,
            @Size(max = 64) String channelSubCode,
            @NotBlank @Size(max = 32) String channelTier,
            @NotBlank String stylePrompt,
            Boolean highRisk,
            Boolean enabled
    ) {
    }

    public record ChannelStyleVO(
            Long id,
            String channelGroupCode,
            String channelSubCode,
            String channelTier,
            String stylePrompt,
            Boolean highRisk,
            Boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ComplianceHitLogVO(
            Long id,
            Long articleId,
            Long batchId,
            Long taskId,
            Long projectId,
            String projectName,
            Long brandId,
            String brandName,
            Long ruleId,
            String ruleType,
            String matchedText,
            String checkStage,
            String action,
            LocalDateTime createdAt
    ) {
    }

    public record GenerationHistoryVO(
            Long id,
            Long projectId,
            String projectName,
            Long brandId,
            String brandName,
            Long topicAngleId,
            String topicAngle,
            String structureSkeleton,
            String focus,
            Long articleId,
            String articleTitle,
            LocalDateTime createdAt
    ) {
    }

    public record WorkbenchOverviewVO(
            Long pendingReviewCount,
            Long rejectedReviewCount,
            Long complianceFailedCount,
            Long discardedCount,
            Long officialPendingCount,
            Long todayHitCount,
            Long sevenDayHitCount,
            Long sevenDayDiscardedCount,
            List<RuleHitSummaryVO> topRuleHits,
            List<BatchTraceVO> recentProblemBatches
    ) {
    }

    public record RuleHitSummaryVO(
            String ruleType,
            Long hitCount
    ) {
    }

    public record BatchTraceVO(
            Long batchId,
            Long projectId,
            String projectName,
            Long brandId,
            String brandName,
            String medicalIndustryCode,
            String medicalChannelTier,
            String topic,
            String status,
            Integer totalCount,
            Integer successCount,
            Integer failedCount,
            Integer discardedCount,
            Integer retryTaskCount,
            LocalDateTime createdAt,
            LocalDateTime finishedAt,
            String errorMessage
    ) {
    }

    public record ComplianceRuleTestRequest(
            @Size(max = 32) String industryCode,
            @Size(max = 32) String channelTier,
            @Size(max = 64) String channelGroupCode,
            @Size(max = 64) String channelSubCode,
            @Size(max = 128) String brandName,
            Integer brandExposureLimit,
            Boolean highRiskChannel,
            @Size(max = 200) String title,
            @NotBlank String content
    ) {
    }

    public record ComplianceRuleTestResultVO(
            Boolean passed,
            List<ComplianceIssueVO> issues
    ) {
    }

    public record ComplianceIssueVO(
            Long ruleId,
            String ruleType,
            String severity,
            String matchedText,
            String message
    ) {
    }
}
