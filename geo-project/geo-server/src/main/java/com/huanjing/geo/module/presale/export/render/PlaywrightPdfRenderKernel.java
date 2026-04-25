package com.huanjing.geo.module.presale.export.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.export.render.ExportRenderConcurrencyException;
import com.huanjing.geo.module.export.render.ExportRenderKernel;
import com.huanjing.geo.module.export.render.ExportRenderProfile;
import com.huanjing.geo.module.export.render.ExportRenderRequest;
import com.huanjing.geo.module.export.render.ExportRenderResult;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PlaywrightPdfRenderKernel implements ExportRenderKernel {

    private final PresaleBrowserManager browserManager;
    private final PresaleExportProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrency;

    public PlaywrightPdfRenderKernel(PresaleBrowserManager browserManager,
                                     PresaleExportProperties properties,
                                     ObjectMapper objectMapper) {
        this.browserManager = browserManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.concurrency = new Semaphore(Math.max(1, properties.getBrowser().getMaxConcurrency()));
    }

    /**
     * Synchronous blocking render entry. The caller owns task-level queueing and should
     * keep concurrency at or below the configured browser capacity.
     */
    @Override
    public ExportRenderResult render(ExportRenderRequest request) throws Exception {
        long started = System.nanoTime();
        ExportRenderProfile profile = request.getProfile();
        try {
            if (!concurrency.tryAcquire(profile.getAcquireTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new ExportRenderConcurrencyException(
                        "Export browser concurrency limit reached after "
                                + profile.getAcquireTimeoutMs() + "ms");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExportRenderConcurrencyException("Interrupted while waiting for export browser slot", ex);
        }
        try {
            Browser browser = browserManager.getBrowser();
            Files.createDirectories(request.getDebugDir());
            List<String> consoleLines = new ArrayList<>();
            List<String> networkLines = new ArrayList<>();

            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setDeviceScaleFactor(profile.getDeviceScaleFactor())
                    .setViewportSize(profile.getViewportWidth(), profile.getViewportHeight()))) {
                Page page = context.newPage();
                page.setDefaultTimeout(profile.getReadyTimeoutMs());
                page.onConsoleMessage(msg -> consoleLines.add(formatConsole(msg)));
                page.onRequestFailed(req -> networkLines.add(formatFailedRequest(req)));
                page.onPageError(err -> consoleLines.add("[pageerror] " + err));

                try {
                    page.navigate(request.getRenderUrl(), new Page.NavigateOptions()
                            .setTimeout((double) profile.getPageLoadTimeoutMs())
                            .setWaitUntil(WaitUntilState.NETWORKIDLE));
                    page.waitForFunction("() => window.__PRESALE_PRINT_READY__ === true",
                            null,
                            new Page.WaitForFunctionOptions().setTimeout((double) profile.getReadyTimeoutMs()));

                    Object metrics = page.evaluate("() => window.__PRESALE_PRINT_METRICS__ || {}");
                    ObjectNode metricsRoot = objectMapper.valueToTree(metrics);
                    long pdfStarted = System.nanoTime();
                    page.pdf(new Page.PdfOptions()
                            .setPath(request.getOutputPath())
                            .setFormat(profile.getPageFormat())
                            .setPrintBackground(true)
                            .setPreferCSSPageSize(true)
                            .setMargin(new Margin()
                                    .setTop("0")
                                    .setRight("0")
                                    .setBottom("0")
                                    .setLeft("0")));
                    metricsRoot.put("pdf_elapsed_ms", Duration.ofNanos(System.nanoTime() - pdfStarted).toMillis());
                    String metricsJson = objectMapper.writeValueAsString(metricsRoot);
                    Files.writeString(request.getDebugDir().resolve("metrics.json"),
                            metricsJson, StandardCharsets.UTF_8);

                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(request.getDebugDir().resolve("screenshot.png"))
                            .setFullPage(true));
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    return ExportRenderResult.builder()
                            .elapsedMs(elapsedMs)
                            .fileSize(Files.size(request.getOutputPath()))
                            .metricsJson(metricsJson)
                            .build();
                } catch (Exception ex) {
                    try {
                        page.screenshot(new Page.ScreenshotOptions()
                                .setPath(request.getDebugDir().resolve("screenshot.png"))
                                .setFullPage(true));
                    } catch (Exception ignored) {
                        // Preserve the original render failure.
                    }
                    throw ex;
                } finally {
                    writePageHtmlQuietly(request, page);
                    writeDebugFileQuietly(request, "console.log", consoleLines);
                    writeDebugFileQuietly(request, "network.log", networkLines);
                }
            }
        } finally {
            concurrency.release();
        }
    }

    private String formatConsole(ConsoleMessage msg) {
        return "[" + msg.type() + "] " + msg.text();
    }

    private String formatFailedRequest(Request req) {
        return "[requestfailed] " + req.method() + " " + req.url() + " " + req.failure();
    }

    private void writeDebugFileQuietly(ExportRenderRequest request, String fileName, List<String> lines) {
        try {
            Files.write(request.getDebugDir().resolve(fileName), lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Failed to write export debug file: {}", fileName, ex);
        }
    }

    private void writePageHtmlQuietly(ExportRenderRequest request, Page page) {
        try {
            Files.writeString(request.getDebugDir().resolve("page.html"), page.content(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Failed to write export page html", ex);
        }
    }
}
