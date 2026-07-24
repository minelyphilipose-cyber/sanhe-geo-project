package com.huanjing.geo.module.project.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.project.dto.BaselineCanonicalReportVO;
import com.huanjing.geo.module.project.dto.BaselineObservationCollectRequest;
import com.huanjing.geo.module.project.dto.BaselineObservationCollectVO;
import com.huanjing.geo.module.project.dto.BaselineReportExportResponse;
import com.huanjing.geo.module.project.dto.BaselineSnapshotCreateRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotReviewRequest;
import com.huanjing.geo.module.project.dto.BaselineSnapshotVO;
import com.huanjing.geo.module.project.service.BaselineCanonicalAggregateService;
import com.huanjing.geo.module.project.service.BaselineObservationCollectionService;
import com.huanjing.geo.module.project.service.BaselineReportExportService;
import com.huanjing.geo.module.project.service.BaselineReportSnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId:\\d+}/baseline-report/snapshots")
@RequiredArgsConstructor
public class BaselineReportSnapshotController {
    private final BaselineReportSnapshotService baselineReportSnapshotService;
    private final BaselineObservationCollectionService baselineObservationCollectionService;
    private final BaselineCanonicalAggregateService baselineCanonicalAggregateService;
    private final BaselineReportExportService baselineReportExportService;

    @PostMapping
    public R<BaselineSnapshotVO> create(@PathVariable Long projectId,
                                        @Valid @RequestBody(required = false) BaselineSnapshotCreateRequest request) {
        return R.ok(baselineReportSnapshotService.create(projectId, request));
    }

    @PutMapping("/{baselineId:\\d+}/review")
    public R<BaselineSnapshotVO> review(@PathVariable Long projectId,
                                        @PathVariable Long baselineId,
                                        @Valid @RequestBody(required = false) BaselineSnapshotReviewRequest request) {
        return R.ok(baselineReportSnapshotService.review(projectId, baselineId, request));
    }

    @PostMapping("/{baselineId:\\d+}/seal")
    public R<BaselineSnapshotVO> seal(@PathVariable Long projectId,
                                      @PathVariable Long baselineId) {
        return R.ok(baselineReportSnapshotService.seal(projectId, baselineId));
    }

    @PostMapping("/{baselineId:\\d+}/collect")
    public R<BaselineObservationCollectVO> collect(@PathVariable Long projectId,
                                                   @PathVariable Long baselineId,
                                                   @RequestBody(required = false) BaselineObservationCollectRequest request) {
        return R.ok(baselineObservationCollectionService.collect(projectId, baselineId, request));
    }

    @GetMapping("/{baselineId:\\d+}/collect/tasks/{taskId:\\d+}")
    public R<BaselineObservationCollectVO> collectStatus(@PathVariable Long projectId,
                                                         @PathVariable Long baselineId,
                                                         @PathVariable Long taskId) {
        return R.ok(baselineObservationCollectionService.status(projectId, baselineId, taskId));
    }

    @GetMapping("/{baselineId:\\d+}/collect/latest")
    public R<BaselineObservationCollectVO> latestCollectStatus(@PathVariable Long projectId,
                                                               @PathVariable Long baselineId) {
        return R.ok(baselineObservationCollectionService.status(projectId, baselineId, null));
    }

    @PostMapping("/{baselineId:\\d+}/collect/tasks/{taskId:\\d+}/cancel")
    public R<BaselineObservationCollectVO> cancelCollect(@PathVariable Long projectId,
                                                         @PathVariable Long baselineId,
                                                         @PathVariable Long taskId) {
        return R.ok(baselineObservationCollectionService.cancel(projectId, baselineId, taskId));
    }

    @PostMapping("/{baselineId:\\d+}/canonical/recompute")
    public R<BaselineCanonicalReportVO> recomputeCanonical(@PathVariable Long projectId,
                                                           @PathVariable Long baselineId) {
        return R.ok(baselineCanonicalAggregateService.recompute(projectId, baselineId));
    }

    @GetMapping("/{baselineId:\\d+}/canonical")
    public R<BaselineCanonicalReportVO> canonical(@PathVariable Long projectId,
                                                  @PathVariable Long baselineId) {
        return R.ok(baselineCanonicalAggregateService.latest(projectId, baselineId));
    }

    @PostMapping("/{baselineId:\\d+}/exports")
    public R<BaselineReportExportResponse> createExport(@PathVariable Long projectId,
                                                        @PathVariable Long baselineId) {
        return R.ok(baselineReportExportService.create(projectId, baselineId, false));
    }

    @GetMapping("/{baselineId:\\d+}/exports/{exportId:\\d+}")
    public R<BaselineReportExportResponse> getExport(@PathVariable Long projectId,
                                                     @PathVariable Long baselineId,
                                                     @PathVariable Long exportId) {
        return R.ok(baselineReportExportService.get(projectId, baselineId, exportId));
    }

    @GetMapping("/{baselineId:\\d+}/exports/{exportId:\\d+}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable Long projectId,
                                                 @PathVariable Long baselineId,
                                                 @PathVariable Long exportId) {
        byte[] bytes = baselineReportExportService.downloadBytes(projectId, baselineId, exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(baselineReportExportService.downloadFileName(projectId, baselineId, exportId))
                        .build()
                        .toString())
                .body(bytes);
    }

    @GetMapping("/latest")
    public R<BaselineSnapshotVO> latest(@PathVariable Long projectId) {
        return R.ok(baselineReportSnapshotService.latest(projectId));
    }

    @GetMapping("/{baselineId:\\d+}")
    public R<BaselineSnapshotVO> get(@PathVariable Long projectId,
                                     @PathVariable Long baselineId) {
        return R.ok(baselineReportSnapshotService.get(projectId, baselineId));
    }
}
