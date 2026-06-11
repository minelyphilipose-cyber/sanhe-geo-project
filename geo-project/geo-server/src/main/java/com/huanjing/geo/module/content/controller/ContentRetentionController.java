package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.service.ArticlePublishRecordCompensationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ContentRetention")
@RestController
@RequestMapping("/api/content/retention")
@RequiredArgsConstructor
public class ContentRetentionController {

    private final ArticlePublishRecordCompensationService articlePublishRecordCompensationService;

    @PostMapping("/article-publish-records/backfill")
    public R<ArticlePublishRecordCompensationService.CompensationResult> backfillArticlePublishRecords(
            @RequestParam(defaultValue = "100") Integer limit,
            @RequestParam(defaultValue = "true") Boolean dryRun
    ) {
        return R.ok(articlePublishRecordCompensationService.backfillPublishedTasks(limit == null ? 100 : limit,
                dryRun == null || dryRun));
    }
}
