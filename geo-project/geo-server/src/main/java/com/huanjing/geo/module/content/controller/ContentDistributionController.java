package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.ArticleDistributeRequest;
import com.huanjing.geo.module.content.dto.DistributionManualConfirmRequest;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.dto.SelfMediaDistributeRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.Map;

@Tag(name = "ContentDistribution")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentDistributionController {

    private final ContentDistributionService contentDistributionService;
    private final BrandService brandService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ProjectMapper projectMapper;

    @PostMapping("/articles/{articleId}/distribute")
    public R<DistributionTask> distribute(@PathVariable Long articleId, @Valid @RequestBody ArticleDistributeRequest req) {
        return R.ok(contentDistributionService.distribute(articleId, req.getSiteId()));
    }

    @PostMapping("/articles/{articleId}/distribute-to-geo-site")
    public R<DistributionTask> distributeToGeoSite(@PathVariable Long articleId,
                                                   @RequestParam Long brandId) {
        Brand brand = brandService.requireBrandWithAccess(brandId, true);
        TargetContext.BrandGeoSiteTarget target =
                new TargetContext.BrandGeoSiteTarget(brand.getId(), brand.getGeoSiteCode());
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }

    @PostMapping("/articles/{articleId}/distribute-to-self-media")
    public R<DistributionTask> distributeToSelfMedia(@PathVariable Long articleId,
                                                     @Valid @RequestBody SelfMediaDistributeRequest req) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(req.getSelfMediaAccountId());
        if (account == null) {
            throw new BizException(404, "Self media account not found");
        }
        TargetContext.SelfMediaTarget target =
                new TargetContext.SelfMediaTarget(
                        account,
                        req.getCoverMaterialId(),
                        req.getImageMaterialIds(),
                        null,
                        parseInteger(req.getPrivateStatus(), "privateStatus"),
                        parseInteger(req.getDownloadType(), "downloadType"),
                        req.getRequestId(),
                        req.getPlatformOptions()
                );
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }

    @PostMapping("/distribution-tasks/{taskId}/refresh-review-status")
    public R<DistributionTask> refreshReviewStatus(@PathVariable Long taskId) {
        DistributionTask task = distributionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "distribution task not found");
        }
        ArticleDraft article = articleDraftMapper.selectById(task.getArticleId());
        if (article == null) {
            throw new BizException(404, "Article not found");
        }
        Project project = projectMapper.selectById(article.getProjectId());
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        brandService.requireBrandWithAccess(project.getBrandId(), true);
        return R.ok(contentDistributionService.refreshDistributionTaskReviewStatus(taskId));
    }

    @GetMapping("/articles/{articleId}/distribution")
    public R<Map<String, Object>> distribution(@PathVariable Long articleId) {
        return R.ok(contentDistributionService.distributionHistory(articleId));
    }

    @PostMapping("/distribution-tasks/{taskId}/retry")
    public R<DistributionTask> retry(@PathVariable Long taskId) {
        return R.ok(contentDistributionService.retry(taskId));
    }

    @PatchMapping("/distribution-tasks/{taskId}/confirm-manual")
    public R<DistributionTask> confirmManual(@PathVariable Long taskId, @Valid @RequestBody DistributionManualConfirmRequest req) {
        return R.ok(contentDistributionService.confirmManual(taskId, req.getPublishedUrl(), req.getResponsePayload()));
    }

    @GetMapping("/projects/{projectId}/publish-quota")
    public R<PublishQuotaVO> quota(@PathVariable Long projectId) {
        return R.ok(contentDistributionService.quota(projectId));
    }

    @GetMapping("/projects/{projectId}/recommended-sites")
    public R<RecommendedSitesResponseVO> recommendedSites(@PathVariable Long projectId) {
        return R.ok(contentDistributionService.recommendedSites(projectId));
    }

    private Integer parseInteger(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(400, fieldName + " must be an integer");
        }
    }
}
