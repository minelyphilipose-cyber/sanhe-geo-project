package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderConfigResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderPreviewRequest;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderPreviewResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleRenderSaveRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.service.render.wechat.WechatArticleRenderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content/articles/{articleId}/wechat-render")
@RequiredArgsConstructor
public class WechatArticleRenderController {
    private final WechatArticleRenderService articleRenderService;
    private final ArticleDraftMapper articleDraftMapper;

    @GetMapping
    public R<ArticleRenderConfigResponse> config(@PathVariable Long articleId) {
        return R.ok(articleRenderService.config(articleId));
    }

    @PostMapping
    public R<ArticleRenderConfigResponse> save(@PathVariable Long articleId,
                                               @Valid @RequestBody ArticleRenderSaveRequest request) {
        return R.ok(articleRenderService.save(articleId, request));
    }

    @PostMapping("/preview")
    public R<ArticleRenderPreviewResponse> preview(@PathVariable Long articleId,
                                                   @RequestBody(required = false) ArticleRenderPreviewRequest request) {
        return R.ok(articleRenderService.preview(requireArticle(articleId),
                request == null ? null : request.getTemplateVersionId(),
                request == null ? null : request.getAnnotations(),
                request == null ? null : request.getRenderConfig()));
    }

    @PostMapping("/final-preview")
    public R<ArticleRenderPreviewResponse> finalPreview(@PathVariable Long articleId,
                                                        @RequestBody(required = false) ArticleRenderPreviewRequest request) {
        return R.ok(articleRenderService.preview(requireArticle(articleId),
                request == null ? null : request.getTemplateVersionId(),
                request == null ? null : request.getAnnotations(),
                request == null ? null : request.getRenderConfig()));
    }

    private ArticleDraft requireArticle(Long articleId) {
        ArticleDraft article = articleDraftMapper.selectById(articleId);
        if (article == null || "deleted".equalsIgnoreCase(article.getStatus())) {
            throw new BizException(404, "Article not found");
        }
        return article;
    }
}
