package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ArticleDistributeRequest;
import com.huanjing.geo.module.content.dto.DistributionManualConfirmRequest;
import com.huanjing.geo.module.content.dto.PublishQuotaVO;
import com.huanjing.geo.module.content.dto.RecommendedSitesResponseVO;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.service.ContentDistributionService;
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

    @PostMapping("/articles/{articleId}/distribute")
    public R<DistributionTask> distribute(@PathVariable Long articleId, @Valid @RequestBody ArticleDistributeRequest req) {
        return R.ok(contentDistributionService.distribute(articleId, req.getSiteId()));
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
