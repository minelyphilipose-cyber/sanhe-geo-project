package com.huanjing.geo.module.report.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.report.dto.ShareVerifyRequest;
import com.huanjing.geo.module.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Tag(name = "Share")
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareReportController {

    private final ReportService reportService;

    @GetMapping("/{token}")
    public R<Map<String, Object>> getByToken(@PathVariable String token, HttpServletRequest request) {
        return R.ok(reportService.getShareReport(token, request));
    }

    @PostMapping("/{token}/verify")
    public R<Map<String, Object>> verifyPassword(@PathVariable String token,
                                                  @Valid @RequestBody ShareVerifyRequest req,
                                                  HttpServletRequest request) {
        return R.ok(reportService.verifySharePassword(token, req.getPassword(), request));
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<Void> downloadPdf(@PathVariable String token) {
        String pdfUrl = reportService.resolveSharePdfUrl(token);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(pdfUrl)).build();
    }
}
