package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.ArticleDistributeRequest;
import com.huanjing.geo.module.content.dto.DistributionManualConfirmRequest;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.dto.WechatMpDistributeRequest;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.ContentDistributionService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.common.exception.BizException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "ContentDistribution")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentDistributionController {

    private final ContentDistributionService contentDistributionService;
    private final BrandService brandService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;

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

    @PostMapping("/articles/{articleId}/distribute-to-mp-account")
    public R<DistributionTask> distributeToMpAccount(@PathVariable Long articleId,
                                                     @Valid @RequestBody WechatMpDistributeRequest req) {
        SelfMediaAccount account = selfMediaAccountMapper.selectById(req.getMpAccountId());
        if (account == null || !"wechat_mp".equalsIgnoreCase(account.getPlatform())) {
            throw new BizException(404, "Mp account not found");
        }
        TargetContext.SelfMediaTarget target =
                new TargetContext.SelfMediaTarget(
                        account,
                        req.getCoverMaterialId(),
                        null,
                        null,
                        null,
                        null,
                        req.getRequestId(),
                        null
                );
        return R.ok(contentDistributionService.distributeTo(articleId, target));
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
}
