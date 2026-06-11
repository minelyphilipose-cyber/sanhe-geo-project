package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.MedicalArticleConstants;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.customer.entity.BrandOperatorAssignment;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandOperatorAssignmentMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialIndustryComplianceAlertService {

    private static final String SOURCE = "special_industry_compliance";
    private static final String FALLBACK_RECIPIENT_ROLE = "manager";
    private static final String ROUTE = "/admin/content/special-industry-compliance";
    private static final List<String> BRAND_OPERATOR_ROLES = List.of("PRIMARY", "SECONDARY");

    private final SystemAlertService systemAlertService;
    private final BrandOperatorAssignmentMapper brandOperatorAssignmentMapper;
    private final CompanyMapper companyMapper;
    private final SysUserMapper sysUserMapper;

    public void notifyComplianceDiscarded(Project project,
                                          Brand brand,
                                          BatchArticleGenerationTask task,
                                          Long articleId,
                                          MedicalArticleComplianceChecker.CheckResult result) {
        if (articleId == null) {
            return;
        }
        try {
            createRecipientAlerts(
                    "special_industry_compliance_discarded",
                    "error",
                    "特殊行业文章合规 3 次校验失败，已废弃留痕",
                    baseContext(project, brand, task, articleId)
                            .with("action", "discarded_compliance_failed")
                            .with("hitRuleTypes", result == null ? null : result.issues().stream()
                                    .map(MedicalArticleComplianceChecker.ComplianceIssue::ruleType)
                                    .filter(StringUtils::hasText)
                                    .distinct()
                                    .toList())
                            .build(),
                    project,
                    "special-industry:compliance-discarded:article:" + articleId
            );
        } catch (Exception ex) {
            log.warn("Create special industry discarded alert failed articleId={}", articleId, ex);
        }
    }

    public void notifyPublishReviewPending(Project project,
                                           Brand brand,
                                           BatchArticleGenerationTask task,
                                           Long articleId,
                                           MedicalArticleGenerationService.MedicalPromptContext context) {
        if (articleId == null
                || context == null
                || !MedicalArticleConstants.TIER_OFFICIAL_SITE.equals(context.channelTier())
                || StringUtils.hasText(context.medicalAdReviewNo())) {
            return;
        }
        try {
            createRecipientAlerts(
                    "special_industry_publish_review_pending",
                    "warn",
                    "特殊行业官网档文章待法务发布确认",
                    baseContext(project, brand, task, articleId)
                            .with("action", "publish_review_pending")
                            .with("medicalChannelTier", context.channelTier())
                            .with("medicalIndustryCode", context.industryCode())
                            .build(),
                    project,
                    publishReviewDedupeKey(articleId)
            );
        } catch (Exception ex) {
            log.warn("Create special industry publish review alert failed articleId={}", articleId, ex);
        }
    }

    public void closePublishReviewPending(ArticleDraft article, Project project, Long operatorId) {
        if (article == null || article.getId() == null) {
            return;
        }
        try {
            systemAlertService.resolveOpenByDedupeKeyPrefix(publishReviewDedupeKey(article.getId()), operatorId);
        } catch (Exception ex) {
            log.warn("Resolve special industry publish review alert failed articleId={}", article.getId(), ex);
        }
    }

    public void notifyPublishReviewRejected(ArticleDraft article, Project project, Long operatorId, String comment) {
        if (article == null || article.getId() == null) {
            return;
        }
        try {
            createRecipientAlerts(
                    "special_industry_publish_review_rejected",
                    "error",
                    "特殊行业官网档文章法务驳回，需运营处理",
                    baseContext(project, null, null, article.getId())
                            .with("action", "publish_review_rejected")
                            .with("operatorId", operatorId)
                            .with("comment", StringUtils.hasText(comment) ? comment.trim() : null)
                            .with("medicalIndustryCode", article.getMedicalIndustryCode())
                            .with("medicalChannelTier", article.getMedicalChannelTier())
                            .build(),
                    project,
                    publishReviewRejectedDedupeKey(article.getId())
            );
        } catch (Exception ex) {
            log.warn("Create special industry publish review rejected alert failed articleId={}", article.getId(), ex);
        }
    }

    public void closePublishReviewRejected(ArticleDraft article, Long operatorId) {
        if (article == null || article.getId() == null) {
            return;
        }
        try {
            systemAlertService.resolveOpenByDedupeKeyPrefix(publishReviewRejectedDedupeKey(article.getId()), operatorId);
        } catch (Exception ex) {
            log.warn("Resolve special industry publish review rejected alert failed articleId={}", article.getId(), ex);
        }
    }

    private void createRecipientAlerts(String alertType,
                                       String severity,
                                       String message,
                                       Map<String, Object> context,
                                       Project project,
                                       String dedupeKeyPrefix) {
        Set<Long> userIds = resolveRecipientUserIds(project);
        for (Long userId : userIds) {
            systemAlertService.createRecipientAlert(
                    alertType,
                    severity,
                    SOURCE,
                    message,
                    new ContextBuilder(context).with("recipientUserId", userId).build(),
                    userId,
                    null,
                    dedupeKeyPrefix + ":user:" + userId
            );
        }
        systemAlertService.createRecipientAlert(
                alertType,
                severity,
                SOURCE,
                message,
                new ContextBuilder(context).with("recipientRole", FALLBACK_RECIPIENT_ROLE).build(),
                null,
                FALLBACK_RECIPIENT_ROLE,
                dedupeKeyPrefix + ":role:" + FALLBACK_RECIPIENT_ROLE
        );
    }

    private Set<Long> resolveRecipientUserIds(Project project) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (project == null) {
            return userIds;
        }
        if (project.getBrandId() != null) {
            List<BrandOperatorAssignment> assignments = brandOperatorAssignmentMapper.selectList(
                    new LambdaQueryWrapper<BrandOperatorAssignment>()
                            .eq(BrandOperatorAssignment::getBrandId, project.getBrandId())
                            .eq(BrandOperatorAssignment::getStatus, "active")
                            .in(BrandOperatorAssignment::getRole, BRAND_OPERATOR_ROLES)
                            .orderByDesc(BrandOperatorAssignment::getAssignedAt, BrandOperatorAssignment::getId));
            for (BrandOperatorAssignment assignment : assignments) {
                addActiveUser(userIds, assignment.getOperatorId());
            }
        }
        if (project.getCompanyId() != null) {
            Company company = companyMapper.selectById(project.getCompanyId());
            if (company != null) {
                addActiveUser(userIds, company.getOwnerId());
            }
        }
        return userIds;
    }

    private void addActiveUser(Set<Long> userIds, Long userId) {
        if (userId == null || userIds.contains(userId)) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
            userIds.add(userId);
        }
    }

    private String publishReviewDedupeKey(Long articleId) {
        return "special-industry:publish-review-pending:article:" + articleId;
    }

    private String publishReviewRejectedDedupeKey(Long articleId) {
        return "special-industry:publish-review-rejected:article:" + articleId;
    }

    private ContextBuilder baseContext(Project project, Brand brand, BatchArticleGenerationTask task, Long articleId) {
        ContextBuilder builder = new ContextBuilder()
                .with("route", ROUTE)
                .with("articleId", articleId);
        if (project != null) {
            builder.with("projectId", project.getId())
                    .with("projectName", project.getProjectName())
                    .with("brandId", project.getBrandId());
        }
        if (brand != null) {
            builder.with("brandName", brand.getBrandName());
        }
        if (task != null) {
            builder.with("batchId", task.getBatchId())
                    .with("taskId", task.getId())
                    .with("topic", task.getTopic())
                    .with("medicalIndustryCode", task.getMedicalIndustryCode())
                    .with("medicalCategoryCode", task.getMedicalCategoryCode());
        }
        return builder;
    }

    private static final class ContextBuilder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private ContextBuilder() {
        }

        private ContextBuilder(Map<String, Object> source) {
            if (source != null) {
                values.putAll(source);
            }
        }

        private ContextBuilder with(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                values.put(key, value);
            }
            return this;
        }

        private Map<String, Object> build() {
            return values;
        }
    }
}
