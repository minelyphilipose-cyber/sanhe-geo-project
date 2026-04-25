package com.huanjing.geo.module.presale.export.render;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Component
public class PresalePdfRenderKernel {

    private final PresaleBrowserManager browserManager;
    private final PresaleExportProperties properties;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrency;

    public PresalePdfRenderKernel(PresaleBrowserManager browserManager,
                                  PresaleExportProperties properties,
                                  ObjectMapper objectMapper) {
        this.browserManager = browserManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.concurrency = new Semaphore(Math.max(1, properties.getBrowser().getMaxConcurrency()));
    }

    public PresalePdfRenderResult render(PresalePdfRenderRequest request) throws Exception {
        long started = System.nanoTime();
        PresaleExportProperties.Browser browserProps = properties.getBrowser();
        if (!concurrency.tryAcquire()) {
            throw new IllegalStateException("Presale export browser concurrency limit reached");
        }
        try {
            Browser browser = browserManager.getBrowser();
            Files.createDirectories(request.getDebugDir());
            List<String> consoleLines = new ArrayList<>();
            List<String> networkLines = new ArrayList<>();

            try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setDeviceScaleFactor(browserProps.getDeviceScaleFactor())
                    .setViewportSize(browserProps.getViewportWidth(), browserProps.getViewportHeight()))) {
                Page page = context.newPage();
                page.setDefaultTimeout(browserProps.getReadyTimeoutMs());
                page.onConsoleMessage(msg -> consoleLines.add(formatConsole(msg)));
                page.onRequestFailed(req -> networkLines.add(formatFailedRequest(req)));
                page.onPageError(err -> consoleLines.add("[pageerror] " + err));

                try {
                    page.navigate(request.getRenderUrl(), new Page.NavigateOptions()
                            .setTimeout((double) browserProps.getPageLoadTimeoutMs())
                            .setWaitUntil(WaitUntilState.NETWORKIDLE));
                    page.waitForFunction("() => window.__PRESALE_PRINT_READY__ === true",
                            null,
                            new Page.WaitForFunctionOptions().setTimeout((double) browserProps.getReadyTimeoutMs()));

                    Object metrics = page.evaluate("() => window.__PRESALE_PRINT_METRICS__ || {}");
                    String metricsJson = objectMapper.writeValueAsString(metrics);
                    Files.writeString(request.getDebugDir().resolve("print-metrics.json"),
                            metricsJson, StandardCharsets.UTF_8);

                    page.pdf(new Page.PdfOptions()
                            .setPath(request.getPdfPath())
                            .setFormat("A4")
                            .setPrintBackground(true)
                            .setPreferCSSPageSize(true)
                            .setMargin(new Margin()
                                    .setTop("0")
                                    .setRight("0")
                                    .setBottom("0")
                                    .setLeft("0")));

                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(request.getDebugDir().resolve("final-page.png"))
                            .setFullPage(true));
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    return PresalePdfRenderResult.builder()
                            .elapsedMs(elapsedMs)
                            .fileSize(Files.size(request.getPdfPath()))
                            .metricsJson(metricsJson)
                            .build();
                } catch (Exception ex) {
                    try {
                        page.screenshot(new Page.ScreenshotOptions()
                                .setPath(request.getDebugDir().resolve("failure.png"))
                                .setFullPage(true));
                    } catch (Exception ignored) {
                        // Preserve the original render failure.
                    }
                    throw ex;
                } finally {
                    Files.write(request.getDebugDir().resolve("console.log"), consoleLines, StandardCharsets.UTF_8);
                    Files.write(request.getDebugDir().resolve("network.log"), networkLines, StandardCharsets.UTF_8);
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
}
