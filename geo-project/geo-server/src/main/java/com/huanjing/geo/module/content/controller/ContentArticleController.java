package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ArticleAiDraftRequest;
import com.huanjing.geo.module.content.dto.ArticleAiDraftResponse;
import com.huanjing.geo.module.content.dto.ArticlePublishRequest;
import com.huanjing.geo.module.content.dto.ArticleResubmitRequest;
import com.huanjing.geo.module.content.dto.ArticleReviewRequest;
import com.huanjing.geo.module.content.dto.ArticleRevisionSaveRequest;
import com.huanjing.geo.module.content.dto.ManualArticleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.service.ArticleAiDraftService;
import com.huanjing.geo.module.content.service.ContentArticleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Tag(name = "ContentArticle")
@RestController
@RequestMapping("/api/content/articles")
@RequiredArgsConstructor
public class ContentArticleController {

    private final ContentArticleService contentArticleService;
    private final ArticleAiDraftService articleAiDraftService;

    @GetMapping
    public R<Page<ArticleDraft>> page(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String articleType,
                                      @RequestParam(defaultValue = "1") Long current,
                                      @RequestParam(defaultValue = "10") Long size) {
        return R.ok(contentArticleService.page(projectId, status, articleType, current, size));
    }

    @PostMapping("/manual")
    public R<ArticleDraft> createManual(@Valid @RequestBody ManualArticleCreateRequest req) {
        return R.ok(contentArticleService.createManual(req));
    }

    @PostMapping("/ai-draft")
    public CompletableFuture<R<ArticleAiDraftResponse>> createAiDraft(@Valid @RequestBody ArticleAiDraftRequest req) {
        return articleAiDraftService.generate(req).thenApply(R::ok);
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

    @PostMapping("/{articleId}/publish")
    public R<Void> publish(@PathVariable Long articleId, @Valid @RequestBody ArticlePublishRequest req) {
        contentArticleService.publish(articleId, req);
        return R.ok();
    }
}
