package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.authoritymedia.MeititejiaProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.ArticleDistributeRequest;
import com.huanjing.geo.module.content.dto.AuthorityMediaDistributeRequest;
import com.huanjing.geo.module.content.dto.BatchArticlePublishRequest;
import com.huanjing.geo.module.content.dto.BatchArticlePublishResponse;
import com.huanjing.geo.module.content.dto.BatchArticlePublishJobSummary;
import com.huanjing.geo.module.content.dto.DistributionManualConfirmRequest;
import com.huanjing.geo.module.content.dto.DistributionSemiAutoAbandonRequest;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.dto.SelfMediaDistributeRequest;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.BatchArticlePublishService;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final BatchArticlePublishService batchArticlePublishService;
    private final BrandService brandService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final MeititejiaProperties meititejiaProperties;

    @PostMapping("/articles/{articleId}/distribute")
    public R<DistributionTask> distribute(@PathVariable Long articleId, @Valid @RequestBody ArticleDistributeRequest req) {
        return R.ok(contentDistributionService.distribute(articleId, req.getSiteId()));
    }

    @PostMapping("/articles/{articleId}/distribute-to-geo-site")
    public R<DistributionTask> distributeToGeoSite(@PathVariable Long articleId,
                                                   @RequestParam Long brandId) {
        Brand brand = brandService.requireBrandWithAccess(brandId, true);
        TargetContext.BrandGeoSiteTarget target = new TargetContext.BrandGeoSiteTarget(brand.getId(), brand.getGeoSiteCode());
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }

    @PostMapping("/articles/{articleId}/distribute-to-industry-site")
    public R<DistributionTask> distributeToIndustrySite(@PathVariable Long articleId,
                                                        @Valid @RequestBody ArticleDistributeRequest req) {
        return R.ok(contentDistributionService.distributeToIndustrySite(articleId, req.getSiteId()));
    }

    @PostMapping("/articles/{articleId}/distribute-to-forum-site")
    public R<DistributionTask> distributeToForumSite(@PathVariable Long articleId,
                                                     @Valid @RequestBody ArticleDistributeRequest req) {
        return R.ok(contentDistributionService.distributeToForumSite(articleId, req.getSiteId(), req.getFid()));
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

    @PostMapping("/articles/{articleId}/distribute-to-authority-media")
    public R<DistributionTask> distributeToAuthorityMedia(@PathVariable Long articleId,
                                                          @Valid @RequestBody AuthorityMediaDistributeRequest req,
                                                          HttpServletRequest servletRequest) {
        TargetContext.AuthorityMediaTarget target = new TargetContext.AuthorityMediaTarget(
                req.getResourceId(),
                req.getSalingPrice(),
                resolvePreviewUrlBase(req.getPreviewUrl(), servletRequest),
                req.getPublishedAt(),
                req.getRemark()
        );
        return R.ok(contentDistributionService.distributeTo(articleId, target));
    }

    @PostMapping("/articles/batch-publish")
    public R<BatchArticlePublishResponse> batchPublish(@Valid @RequestBody BatchArticlePublishRequest req) {
        return R.ok(batchArticlePublishService.submit(req));
    }

    @GetMapping("/articles/batch-publish")
    public R<Page<BatchArticlePublishJobSummary>> batchPublishPage(@RequestParam(defaultValue = "1") Long current,
                                                                   @RequestParam(defaultValue = "10") Long size,
                                                                   @RequestParam(required = false) String status) {
        return R.ok(batchArticlePublishService.page(current, size, status));
    }

    @GetMapping("/articles/batch-publish/{jobId}")
    public R<BatchArticlePublishResponse> batchPublishDetail(@PathVariable Long jobId) {
        return R.ok(batchArticlePublishService.response(jobId));
    }

    @PostMapping("/distribution-tasks/{taskId}/refresh-review-status")
    public R<DistributionTask> refreshReviewStatus(@PathVariable Long taskId) {
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

    @PatchMapping("/distribution-tasks/{taskId}/confirm-semi-auto")
    public R<DistributionTask> confirmSemiAuto(@PathVariable Long taskId, @Valid @RequestBody DistributionManualConfirmRequest req) {
        return R.ok(contentDistributionService.confirmSemiAuto(taskId, req.getPublishedUrl(), req.getResponsePayload()));
    }

    @PatchMapping("/distribution-tasks/{taskId}/abandon-semi-auto")
    public R<DistributionTask> abandonSemiAuto(@PathVariable Long taskId, @RequestBody(required = false) DistributionSemiAutoAbandonRequest req) {
        String reason = req == null ? null : req.getReason();
        return R.ok(contentDistributionService.abandonSemiAuto(taskId, reason));
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

    private String resolvePreviewUrlBase(String previewUrlBase, HttpServletRequest request) {
        if (StringUtils.hasText(previewUrlBase)) {
            return previewUrlBase.trim().replaceAll("/+$", "");
        }
        String base = meititejiaProperties.getPreviewUrlBase();
        if (!StringUtils.hasText(base)) {
            String scheme = request.getHeader("X-Forwarded-Proto");
            if (!StringUtils.hasText(scheme)) {
                scheme = request.getScheme();
            }
            String host = request.getHeader("X-Forwarded-Host");
            if (!StringUtils.hasText(host)) {
                host = request.getHeader("Host");
            }
            base = scheme + "://" + host;
        }
        return base.replaceAll("/+$", "");
    }
}
