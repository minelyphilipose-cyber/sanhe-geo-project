package com.huanjing.geo.module.presale.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketDraftUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketMappingUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleIndustryBucketRejectRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleHeatmapSummaryUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketCreateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleLexiconBucketUpdateRequest;
import com.huanjing.geo.module.presale.dto.request.PresaleNarrativeFindingCopyUpdateRequest;
import com.huanjing.geo.module.presale.dto.response.PresaleNarrativeConfigAdminResponse;
import com.huanjing.geo.module.presale.persist.entity.PresaleHeatmapSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketMapping;
import com.huanjing.geo.module.presale.persist.entity.PresaleIndustryBucketReviewTask;
import com.huanjing.geo.module.presale.persist.entity.PresaleLexiconBucket;
import com.huanjing.geo.module.presale.persist.entity.PresaleNarrativeFindingCopy;
import com.huanjing.geo.module.presale.service.PresaleNarrativeConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presale/narrative-config")
@RequiredArgsConstructor
public class PresaleNarrativeConfigController {

    private final PresaleNarrativeConfigAdminService service;

    @GetMapping
    public R<PresaleNarrativeConfigAdminResponse> get() {
        return R.ok(service.getConfig());
    }

    @PutMapping("/finding-copy/{id}")
    public R<PresaleNarrativeFindingCopy> updateFindingCopy(@PathVariable Long id,
                                                            @Valid @RequestBody PresaleNarrativeFindingCopyUpdateRequest req) {
        return R.ok(service.updateFindingCopy(id, req));
    }

    @PutMapping("/heatmap-summary/{id}")
    public R<PresaleHeatmapSummary> updateHeatmapSummary(@PathVariable Long id,
                                                         @Valid @RequestBody PresaleHeatmapSummaryUpdateRequest req) {
        return R.ok(service.updateHeatmapSummary(id, req));
    }

    @PostMapping("/lexicon-task/{id}/draft")
    public R<PresaleIndustryBucketReviewTask> draftIndustryBucket(@PathVariable Long id) {
        return R.ok(service.draftIndustryBucket(id));
    }

    @PutMapping("/lexicon-task/{id}/draft")
    public R<PresaleIndustryBucketReviewTask> updateIndustryBucketDraft(@PathVariable Long id,
                                                                        @Valid @RequestBody PresaleIndustryBucketDraftUpdateRequest req) {
        return R.ok(service.updateIndustryBucketDraft(id, req));
    }

    @PostMapping("/lexicon-task/{id}/approve")
    public R<PresaleIndustryBucketReviewTask> approveIndustryBucketTask(@PathVariable Long id) {
        return R.ok(service.approveIndustryBucketTask(id));
    }

    @PostMapping("/lexicon-task/{id}/reject")
    public R<PresaleIndustryBucketReviewTask> rejectIndustryBucketTask(@PathVariable Long id,
                                                                       @RequestBody(required = false) PresaleIndustryBucketRejectRequest req) {
        return R.ok(service.rejectIndustryBucketTask(id, req));
    }

    @PostMapping("/lexicon-bucket")
    public R<PresaleLexiconBucket> createLexiconBucket(@Valid @RequestBody PresaleLexiconBucketCreateRequest req) {
        return R.ok(service.createLexiconBucket(req));
    }

    @PutMapping("/lexicon-bucket/{id}")
    public R<PresaleLexiconBucket> updateLexiconBucket(@PathVariable Long id,
                                                       @Valid @RequestBody PresaleLexiconBucketUpdateRequest req) {
        return R.ok(service.updateLexiconBucket(id, req));
    }

    @PutMapping("/industry-bucket-mapping/{id}")
    public R<PresaleIndustryBucketMapping> updateIndustryBucketMapping(@PathVariable Long id,
                                                                       @Valid @RequestBody PresaleIndustryBucketMappingUpdateRequest req) {
        return R.ok(service.updateIndustryBucketMapping(id, req));
    }
}
