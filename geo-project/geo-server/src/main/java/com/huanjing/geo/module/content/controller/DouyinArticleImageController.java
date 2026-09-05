package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.service.DouyinArticleImageService;
import com.huanjing.geo.module.content.service.DouyinArticleImageService.BatchView;
import com.huanjing.geo.module.content.service.DouyinArticleImageService.GenerateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/content/articles/{articleId}/douyin-images")
@RequiredArgsConstructor
public class DouyinArticleImageController {
    private final DouyinArticleImageService service;

    @GetMapping
    public R<List<BatchView>> list(@PathVariable Long articleId) {
        return R.ok(service.list(articleId));
    }

    @PostMapping
    public R<BatchView> generate(@PathVariable Long articleId, @RequestBody GenerateRequest request) {
        return R.ok(service.generate(articleId, request));
    }

    @PostMapping("/{batchId}/retry")
    public R<BatchView> retry(@PathVariable Long articleId, @PathVariable Long batchId) {
        return R.ok(service.retry(articleId, batchId));
    }

    @PostMapping("/{batchId}/apply")
    public R<BatchView> apply(@PathVariable Long articleId, @PathVariable Long batchId) {
        return R.ok(service.apply(articleId, batchId));
    }
}
