package com.huanjing.geo.module.presale.export.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.export.dto.PresaleExportCancelRequest;
import com.huanjing.geo.module.presale.export.dto.PresaleExportCreateRequest;
import com.huanjing.geo.module.presale.export.dto.PresaleExportResponse;
import com.huanjing.geo.module.presale.export.dto.PresaleExportRetryRequest;
import com.huanjing.geo.module.presale.export.service.PresaleReportExportCancelService;
import com.huanjing.geo.module.presale.export.service.PresaleReportExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/presale/reports/{reportId}/exports")
@RequiredArgsConstructor
public class PresaleReportExportController {
    private final PresaleReportExportService exportService;
    private final PresaleReportExportCancelService cancelService;

    @PostMapping
    public R<PresaleExportResponse> create(@PathVariable Long reportId,
                                           @RequestBody @Valid PresaleExportCreateRequest req) {
        return R.ok(exportService.create(reportId, req));
    }

    @GetMapping("/{exportId}")
    public R<PresaleExportResponse> get(@PathVariable Long reportId, @PathVariable Long exportId) {
        return R.ok(exportService.get(reportId, exportId));
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long reportId,
                                           @PathVariable Long exportId) {
        byte[] bytes = exportService.downloadBytes(reportId, exportId);
        String fileName = "presale-report-" + reportId + "-" + exportId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(bytes);
    }

    @PostMapping("/{exportId}/retry")
    public R<PresaleExportResponse> retry(@PathVariable Long reportId,
                                          @PathVariable Long exportId,
                                          @RequestBody(required = false) PresaleExportRetryRequest req) {
        return R.ok(exportService.retry(reportId, exportId));
    }

    @PostMapping("/{exportId}/cancel")
    public R<PresaleExportResponse> cancel(@PathVariable Long reportId,
                                           @PathVariable Long exportId,
                                           @RequestBody(required = false) PresaleExportCancelRequest req) {
        return R.ok(cancelService.cancel(reportId, exportId));
    }
}
