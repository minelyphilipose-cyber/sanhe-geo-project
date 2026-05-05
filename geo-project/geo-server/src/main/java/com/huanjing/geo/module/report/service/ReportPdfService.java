package com.huanjing.geo.module.report.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.report.entity.Report;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final String CHROMIUM_LAUNCH_ARGS_ENV = "PLAYWRIGHT_CHROMIUM_LAUNCH_ARGS";

    private final MinioStorageService minioStorageService;

    @Value("${geo.report.web-base-url:http://127.0.0.1:5173}")
    private String reportWebBaseUrl;

    @Value("${geo.report.render-timeout-ms:30000}")
    private int renderTimeoutMs;

    public String generateAndUpload(Report report) {
        if (report == null || !StringUtils.hasText(report.getShareToken())) {
            throw new BizException(400, "Report share token is missing");
        }
        String objectKey = buildPdfObjectKey(report);
        byte[] pdfBytes = renderByBrowser(report.getShareToken());
        return minioStorageService.uploadBytes(pdfBytes, objectKey, "application/pdf");
    }

    private byte[] renderByBrowser(String shareToken) {
        String base = reportWebBaseUrl.endsWith("/") ? reportWebBaseUrl.substring(0, reportWebBaseUrl.length() - 1) : reportWebBaseUrl;
        String renderUrl = base + "/r/" + shareToken + "?print=1";
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setArgs(chromiumLaunchArgs()));
             BrowserContext context = browser.newContext(
                     new Browser.NewContextOptions()
                             .setLocale("zh-CN")
                             .setTimezoneId("Asia/Shanghai")
                             .setViewportSize(1440, 2200)
             )) {
            Page page = context.newPage();
            page.navigate(renderUrl, new Page.NavigateOptions()
                    .setTimeout((double) renderTimeoutMs)
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));
            page.waitForSelector(".share-page__report", new Page.WaitForSelectorOptions().setTimeout((double) renderTimeoutMs));
            page.addStyleTag(new Page.AddStyleTagOptions().setContent("*{animation:none!important;transition:none!important;}"));
            return page.pdf(new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true)
                    .setMargin(new Margin()
                            .setTop("10mm")
                            .setRight("10mm")
                            .setBottom("10mm")
                            .setLeft("10mm")));
        } catch (Exception ex) {
            log.error("Render report pdf by browser failed, url={}, err={}", renderUrl, ex.getMessage(), ex);
            throw new BizException(500, "Render report PDF failed");
        }
    }

    public String buildPdfObjectKey(Report report) {
        return String.format("reports/%d/%s/latest.pdf", report.getProjectId(), report.getReportType());
    }

    private List<String> chromiumLaunchArgs() {
        String raw = System.getenv(CHROMIUM_LAUNCH_ARGS_ENV);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.trim().split("\\s+"))
                .filter(arg -> !arg.isBlank())
                .toList();
    }
}
