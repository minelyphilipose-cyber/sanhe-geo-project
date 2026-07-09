package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ArticleAiDraftPreviewRequest;
import com.huanjing.geo.module.content.dto.ArticleAiDraftPreviewResponse;
import com.huanjing.geo.module.content.dto.ArticleAiDraftRequest;
import com.huanjing.geo.module.content.dto.ArticleAiDraftResponse;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ArticleRevisionSaveRequest;
import com.huanjing.geo.module.content.dto.ArticleTemplatePreviewRequest;
import com.huanjing.geo.module.content.dto.ArticleTemplatePreviewResponse;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.ReadinessReport;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.ReadinessRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.dto.BatchArticleGenerationBatchSummary;
import com.huanjing.geo.module.content.dto.BatchArticleGenerationDetailResponse;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.dto.MedicalPublishReviewRequest;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchRequest;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.ArticleAiDraftService;
import com.huanjing.geo.module.content.service.ArticleGenerationReadinessService;
import com.huanjing.geo.module.content.service.ArticleSelfMediaCookieStatusService;
import com.huanjing.geo.module.content.service.BatchArticleGenerationService;
import com.huanjing.geo.module.content.service.ContentArticleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Tag(name = "ContentArticle")
@RestController
@RequestMapping("/api/content/articles")
@RequiredArgsConstructor
public class ContentArticleController {

    private final ContentArticleService contentArticleService;
    private final ArticleAiDraftService articleAiDraftService;
    private final ArticleSelfMediaCookieStatusService selfMediaCookieStatusService;
    private final BatchArticleGenerationService batchArticleGenerationService;
    private final ArticleGenerationReadinessService articleGenerationReadinessService;

    @GetMapping
    public R<Page<ArticleDraft>> page(@RequestParam(required = false) Long articleId,
                                      @RequestParam(required = false) String projectName,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String articleType,
                                      @RequestParam(required = false) String articleTypeCode,
                                      @RequestParam(required = false) String channelGroupCode,
                                      @RequestParam(required = false) String channelSubCode,
                                      @RequestParam(required = false) String generationMode,
                                      @RequestParam(required = false) String complianceStatus,
                                      @RequestParam(required = false) String publishReviewStatus,
                                      @RequestParam(required = false) String medicalIndustryCode,
                                      @RequestParam(required = false) String medicalChannelTier,
                                      @RequestParam(required = false) Boolean specialIndustryOnly,
                                      @RequestParam(required = false) String createdStartDate,
                                      @RequestParam(required = false) String createdEndDate,
                                      @RequestParam(defaultValue = "1") Long current,
                                      @RequestParam(defaultValue = "10") Long size) {
        return R.ok(contentArticleService.page(articleId, projectName, status, articleType, articleTypeCode,
                channelGroupCode, channelSubCode, generationMode, complianceStatus, publishReviewStatus,
                medicalIndustryCode, medicalChannelTier, specialIndustryOnly, createdStartDate, createdEndDate, current, size));
    }

    @PostMapping("/manual")
    public R<ArticleDraft> createManual(@Valid @RequestBody ManualArticleCreateRequest req) {
        return R.ok(contentArticleService.createManual(req));
    }

    @PostMapping("/ai-draft")
    public CompletableFuture<R<ArticleAiDraftResponse>> createAiDraft(@Valid @RequestBody ArticleAiDraftRequest req) {
        return articleAiDraftService.generate(req).thenApply(R::ok);
    }

    @PostMapping("/ai-draft/preview")
    public R<ArticleAiDraftPreviewResponse> previewAiDraft(
            @Valid @RequestBody ArticleAiDraftPreviewRequest req
    ) throws Exception {
        return R.ok(await(articleAiDraftService.preview(req)));
    }

    @PostMapping("/template-preview")
    public R<ArticleTemplatePreviewResponse> templatePreview(
            @Valid @RequestBody ArticleTemplatePreviewRequest req
    ) throws Exception {
        return R.ok(await(articleAiDraftService.templatePreview(req)));
    }

    @PostMapping("/template-generate")
    public R<ArticleAiDraftResponse> templateGenerate(
            @Valid @RequestBody ArticleTemplatePreviewRequest req
    ) throws Exception {
        return R.ok(await(articleAiDraftService.templateGenerate(req)));
    }

    @PostMapping("/batch-generate")
    public R<BatchArticleGenerateResponse> batchGenerate(@Valid @RequestBody BatchArticleGenerateRequest req) {
        return R.ok(batchArticleGenerationService.create(req));
    }

    @GetMapping("/batch-generate")
    public R<Page<BatchArticleGenerationBatchSummary>> batchGeneratePage(@RequestParam(defaultValue = "1") Long current,
                                                                         @RequestParam(defaultValue = "10") Long size,
                                                                         @RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) String projectName) {
        return R.ok(batchArticleGenerationService.page(current, size, status, projectName));
    }

    @PostMapping("/batch-generate/readiness")
    public R<ReadinessReport> batchGenerateReadiness(@Valid @RequestBody ReadinessRequest req) {
        return R.ok(articleGenerationReadinessService.inspect(req.getProjectId(), req.getQuestionSceneCodes()));
    }

    @GetMapping("/batch-generate/{batchId}")
    public R<BatchArticleGenerationDetailResponse> batchGenerateDetail(@PathVariable Long batchId) {
        return R.ok(batchArticleGenerationService.detail(batchId));
    }

    @PostMapping("/batch-generate/{batchId}/retry-failed")
    public R<BatchArticleGenerationDetailResponse> retryFailedBatchGenerate(@PathVariable Long batchId) {
        return R.ok(batchArticleGenerationService.retryFailed(batchId));
    }

    @PostMapping("/self-media-cookie-status/batch")
    public R<SelfMediaCookieStatusBatchResponse> selfMediaCookieStatusBatch(
            @Valid @RequestBody SelfMediaCookieStatusBatchRequest req
    ) {
        return R.ok(selfMediaCookieStatusService.batch(req));
    }

    @GetMapping("/{articleId}")
    public R<Map<String, Object>> detail(@PathVariable Long articleId) {
        return R.ok(contentArticleService.detail(articleId));
    }

    @PostMapping("/{articleId}/revision")
    public R<Void> saveRevision(@PathVariable Long articleId, @Valid @RequestBody ArticleRevisionSaveRequest req) {
        contentArticleService.saveRevision(articleId, req);
        return R.ok();
    }

    @PostMapping("/{articleId}/resubmit")
    public R<Void> resubmit(@PathVariable Long articleId, @RequestBody(required = false) ArticleResubmitRequest req) {
        contentArticleService.resubmit(articleId, req == null ? new ArticleResubmitRequest() : req);
        return R.ok();
    }

    @PostMapping("/{articleId}/review")
    public R<Void> review(@PathVariable Long articleId, @Valid @RequestBody ArticleReviewRequest req) {
        contentArticleService.review(articleId, req);
        return R.ok();
    }

    @PostMapping("/{articleId}/medical-publish-review")
    public R<Void> reviewMedicalPublish(@PathVariable Long articleId,
                                        @Valid @RequestBody MedicalPublishReviewRequest req) {
        contentArticleService.reviewMedicalPublish(articleId, req);
        return R.ok();
    }

    @DeleteMapping("/{articleId}")
    public R<Void> delete(@PathVariable Long articleId) {
        contentArticleService.deleteUnpublished(articleId);
        return R.ok();
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    @PostMapping("/{articleId}/publish")
    public R<Void> publish(@PathVariable Long articleId, @Valid @RequestBody ArticlePublishRequest req) {
        contentArticleService.publish(articleId, req);
        return R.ok();
    }
}
