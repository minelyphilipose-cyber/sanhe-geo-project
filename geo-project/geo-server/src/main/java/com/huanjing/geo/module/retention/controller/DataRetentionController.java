package com.huanjing.geo.module.retention.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunRequest;
import com.huanjing.geo.module.content.dto.ArticleArchiveDryRunResponse;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeRequest;
import com.huanjing.geo.module.content.dto.ArticleBodyPurgeResponse;
import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunResponse;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunRequest;
import com.huanjing.geo.module.presale.dto.DataRetentionSlimDryRunResponse;
import com.huanjing.geo.module.retention.dto.ContentUrlRewriteRequest;
import com.huanjing.geo.module.retention.dto.ContentUrlRewriteResponse;
import com.huanjing.geo.module.retention.dto.ObjectStorageRetentionDryRunRequest;
import com.huanjing.geo.module.retention.dto.ObjectStorageRetentionDryRunResponse;
import com.huanjing.geo.module.retention.dto.ObjectStorageMigrationRequest;
import com.huanjing.geo.module.retention.dto.ObjectStorageMigrationResponse;
import com.huanjing.geo.module.retention.service.ContentUrlRewriteService;
import com.huanjing.geo.module.retention.service.ObjectStorageMigrationService;
import com.huanjing.geo.module.retention.service.ObjectStorageRetentionDryRunService;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
import com.huanjing.geo.module.retention.service.DataRetentionSlimDryRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DataRetention")
@RestController
@RequestMapping("/api/data-retention")
@RequiredArgsConstructor
public class DataRetentionController {

    private final DataRetentionSlimDryRunService dataRetentionSlimDryRunService;
    private final ArticleRetentionDryRunService articleRetentionDryRunService;
    private final ArticleBodyPurgeService articleBodyPurgeService;
    private final PollRetentionDryRunService pollRetentionDryRunService;
    private final ObjectStorageRetentionDryRunService objectStorageRetentionDryRunService;
    private final ObjectStorageMigrationService objectStorageMigrationService;
    private final ContentUrlRewriteService contentUrlRewriteService;

    @PostMapping("/slim/dry-run")
    public R<DataRetentionSlimDryRunResponse> slimDryRun(@RequestBody DataRetentionSlimDryRunRequest request) {
        return R.ok(dataRetentionSlimDryRunService.dryRun(request == null ? new DataRetentionSlimDryRunRequest() : request));
    }

    @PostMapping("/articles/archive/dry-run")
    public R<ArticleArchiveDryRunResponse> articleArchiveDryRun(@RequestBody ArticleArchiveDryRunRequest request) {
        return R.ok(articleRetentionDryRunService.dryRunArchive(request == null ? new ArticleArchiveDryRunRequest() : request));
    }

    @PostMapping("/articles/archive")
    public R<ArticleArchiveDryRunResponse> articleArchive(@RequestBody ArticleArchiveDryRunRequest request) {
        return R.ok(articleRetentionDryRunService.archive(request == null ? new ArticleArchiveDryRunRequest() : request));
    }

    @PostMapping("/articles/purge/dry-run")
    public R<ArticleBodyPurgeResponse> articlePurgeDryRun(@RequestBody ArticleBodyPurgeRequest request) {
        return R.ok(articleBodyPurgeService.dryRun(
                request == null ? new ArticleBodyPurgeRequest() : request));
    }

    @PostMapping("/articles/purge")
    public R<ArticleBodyPurgeResponse> articlePurge(@RequestBody ArticleBodyPurgeRequest request) {
        return R.ok(articleBodyPurgeService.purge(
                request == null ? new ArticleBodyPurgeRequest() : request));
    }

    @PostMapping("/poll-results/dry-run")
    public R<PollRetentionDryRunResponse> pollResultsDryRun(@RequestBody PollRetentionDryRunRequest request) {
        return R.ok(pollRetentionDryRunService.dryRun(request == null ? new PollRetentionDryRunRequest() : request));
    }

    @PostMapping("/poll-results")
    public R<PollRetentionDryRunResponse> pollResultsPurge(@RequestBody PollRetentionDryRunRequest request) {
        return R.ok(pollRetentionDryRunService.purge(
                request == null ? new PollRetentionDryRunRequest() : request));
    }

    @PostMapping("/object-storage/orphans/dry-run")
    public R<ObjectStorageRetentionDryRunResponse> objectStorageOrphansDryRun(
            @RequestBody ObjectStorageRetentionDryRunRequest request) {
        return R.ok(objectStorageRetentionDryRunService.dryRun(
                request == null ? new ObjectStorageRetentionDryRunRequest() : request));
    }

    @PostMapping("/object-storage/migrate")
    public R<ObjectStorageMigrationResponse> objectStorageMigrate(
            @RequestBody ObjectStorageMigrationRequest request) {
        return R.ok(objectStorageMigrationService.migrate(
                request == null ? new ObjectStorageMigrationRequest() : request));
    }

    @PostMapping("/content-url-rewrite/dry-run")
    public R<ContentUrlRewriteResponse> contentUrlRewriteDryRun(
            @RequestBody ContentUrlRewriteRequest request) {
        return R.ok(contentUrlRewriteService.dryRun(
                request == null ? new ContentUrlRewriteRequest() : request));
    }

    @PostMapping("/content-url-rewrite")
    public R<ContentUrlRewriteResponse> contentUrlRewrite(
            @RequestBody ContentUrlRewriteRequest request) {
        return R.ok(contentUrlRewriteService.rewrite(
                request == null ? new ContentUrlRewriteRequest() : request));
    }
}
