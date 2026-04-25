package com.huanjing.geo.module.presale.poc;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Local Windows PoC runner for presale Playwright PDF rendering.
 *
 * <p>Run with:
 * mvn -q test-compile exec:java "-Dexec.classpathScope=test"
 * "-Dexec.mainClass=com.huanjing.geo.module.presale.poc.PresalePrintPocRunner"
 * "-Dexec.args=1"</p>
 */
public class PresalePrintPocRunner {

    private static final String WEB_BASE_URL = "http://127.0.0.1:3000";
    private static final int BROWSER_START_TIMEOUT_MS = 10_000;
    private static final int PAGE_LOAD_TIMEOUT_MS = 30_000;
    private static final int READY_TIMEOUT_MS = 60_000;
    private static final int PDF_TIMEOUT_MS = 60_000;
    private static final Set<Long> RUN_BASELINE_CHROMIUM_PIDS = readChromiumPids();

    public static void main(String[] args) throws Exception {
        long reportId = args.length > 0 ? Long.parseLong(args[0]) : 1L;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        long javaPid = ProcessHandle.current().pid();
        Path runDir = Path.of("target", "poc",
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
        Files.createDirectories(runDir);

        System.out.println("POC_RUN_DIR=" + runDir.toAbsolutePath());
        System.out.println("JAVA_PID=" + javaPid);
        System.out.println("REPORT_ID=" + reportId);
        System.out.println("ITERATIONS=" + iterations);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = timed("browser.start", BROWSER_START_TIMEOUT_MS, () ->
                    playwright.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)));
            Files.writeString(runDir.resolve("processes-after-browser-start.txt"),
                    listBrowserLikeProcesses(), StandardCharsets.UTF_8);

            try {
                runStaticPoc(browser, runDir);
                List<RenderResult> results = new ArrayList<>();
                for (int i = 1; i <= iterations; i++) {
                    results.add(runPresalePoc(browser, reportId, runDir,
                            "presale-poc-" + i + ".pdf", javaPid, i));
                    sleepQuietly(1_000L);
                }
                printComparison(results);
            } finally {
                browser.close();
            }
        }
    }

    private static void runStaticPoc(Browser browser, Path runDir) throws Exception {
        Path pdf = runDir.resolve("static-poc.pdf");
        long started = System.nanoTime();
        try (BrowserContext context = newContext(browser)) {
            Page page = context.newPage();
            page.setDefaultTimeout(PAGE_LOAD_TIMEOUT_MS);
            page.setContent("""
                    <!doctype html>
                    <html lang="zh-CN">
                    <head><meta charset="utf-8"><style>
                    body{font-family:'Microsoft YaHei','Noto Sans CJK SC',sans-serif;padding:48px}
                    h1{font-size:28px}
                    </style></head>
                    <body>
                    <h1>幻境AI售前报表中文复制验证</h1>
                    <p>Playwright Windows local PDF PoC.</p>
                    </body></html>
                    """, new Page.SetContentOptions().setTimeout((double) PAGE_LOAD_TIMEOUT_MS));
            page.waitForFunction("() => document.fonts.ready");
            timed("static.pdf", PDF_TIMEOUT_MS, () -> page.pdf(pdfOptions(pdf)));
        }
        long elapsedMs = elapsedMs(started);
        PdfAssert pdfAssert = inspectPdf(pdf);
        System.out.printf(Locale.ROOT,
                "POC_0A pdf=%s size=%dKB pages=%d textOk=%s elapsedMs=%d pass=%s%n",
                pdf.toAbsolutePath(),
                Files.size(pdf) / 1024,
                pdfAssert.pages,
                pdfAssert.text.contains("幻境AI售前报表中文复制验证"),
                elapsedMs,
                Files.size(pdf) >= 20 * 1024
                        && pdfAssert.text.contains("幻境AI售前报表中文复制验证")
                        && elapsedMs <= 15_000);
    }

    private static RenderResult runPresalePoc(Browser browser, long reportId, Path runDir,
                                             String fileName, long javaPid, int iteration) throws Exception {
        Path debugDir = runDir.resolve("debug-" + fileName.replace(".pdf", ""));
        Files.createDirectories(debugDir);
        Path pdf = runDir.resolve(fileName);
        List<String> consoleLines = new ArrayList<>();
        List<String> networkLines = new ArrayList<>();
        MemorySampler sampler = new MemorySampler(javaPid);
        long started = System.nanoTime();
        String url = WEB_BASE_URL + "/presale-print-poc/" + reportId;

        try (BrowserContext context = newContext(browser)) {
            Page page = context.newPage();
            page.setDefaultTimeout(READY_TIMEOUT_MS);
            page.onConsoleMessage(msg -> consoleLines.add(formatConsole(msg)));
            page.onRequestFailed(req -> networkLines.add(formatFailedRequest(req)));
            page.onPageError(err -> consoleLines.add("[pageerror] " + err));

            sampler.start();
            try {
                timed("page.navigate", PAGE_LOAD_TIMEOUT_MS, () -> {
                    page.navigate(url, new Page.NavigateOptions()
                            .setTimeout((double) PAGE_LOAD_TIMEOUT_MS)
                            .setWaitUntil(WaitUntilState.NETWORKIDLE));
                    return null;
                });
                timed("print.ready", READY_TIMEOUT_MS, () -> {
                    page.waitForFunction("() => window.__PRESALE_PRINT_READY__ === true",
                            null,
                            new Page.WaitForFunctionOptions().setTimeout((double) READY_TIMEOUT_MS));
                    return null;
                });
                Object metrics = page.evaluate("() => window.__PRESALE_PRINT_METRICS__");
                Files.writeString(debugDir.resolve("print-metrics.txt"),
                        String.valueOf(metrics), StandardCharsets.UTF_8);
                System.out.println("PRINT_METRICS " + metrics);

                timed("presale.pdf", PDF_TIMEOUT_MS, () -> page.pdf(pdfOptions(pdf)));
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(debugDir.resolve("final-page.png"))
                        .setFullPage(true));
            } catch (Exception ex) {
                dumpDebug(debugDir, consoleLines, networkLines);
                try {
                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(debugDir.resolve("failure.png"))
                            .setFullPage(true));
                } catch (Exception ignored) {
                    // Preserve the original failure.
                }
                throw ex;
            } finally {
                sampler.stop();
                dumpDebug(debugDir, consoleLines, networkLines);
            }
        }

        PdfAssert pdfAssert = inspectPdf(pdf);
        long elapsedMs = elapsedMs(started);
        boolean metricsOk = isPrintMetricsOk(Files.readString(debugDir.resolve("print-metrics.txt"), StandardCharsets.UTF_8));
        boolean bottomBandOk = pdfAssert.bottomBandWarnings.isEmpty();
        long afterClosePrivateBytes = readProcessTreePrivateBytes(javaPid, RUN_BASELINE_CHROMIUM_PIDS);
        RenderResult result = new RenderResult(fileName, elapsedMs, Files.size(pdf), pdfAssert.pages,
                pdfAssert.text, sampler.maxPrivateBytes());
        System.out.printf(Locale.ROOT,
                "POC_0B iteration=%d file=%s size=%dKB pages=%d textBrandOrTitle=%s metricsOk=%s bottomBandOk=%s bottomBandWarnings=%s elapsedMs=%d maxPrivateMb=%.1f afterCloseMb=%.1f pass=%s%n",
                iteration,
                pdf.toAbsolutePath(),
                result.bytes / 1024,
                result.pages,
                containsPresaleText(result.text),
                metricsOk,
                bottomBandOk,
                pdfAssert.bottomBandWarnings,
                result.elapsedMs,
                result.maxPrivateBytes / 1024.0 / 1024.0,
                afterClosePrivateBytes / 1024.0 / 1024.0,
                result.pages == 18
                        && containsPresaleText(result.text)
                        && metricsOk
                        && bottomBandOk
                        && result.elapsedMs <= 60_000
                        && result.maxPrivateBytes <= 1_200L * 1024L * 1024L);
        return result;
    }

    private static BrowserContext newContext(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions()
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai")
                .setDeviceScaleFactor(2)
                .setViewportSize(1440, 2200));
    }

    private static Page.PdfOptions pdfOptions(Path pdf) {
        return new Page.PdfOptions()
                .setPath(pdf)
                .setFormat("A4")
                .setPrintBackground(true)
                .setPreferCSSPageSize(true)
                .setMargin(new Margin()
                        .setTop("0")
                        .setRight("0")
                        .setBottom("0")
                        .setLeft("0"));
    }

    private static <T> T timed(String phase, int timeoutMs, Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            T value = supplier.get();
            long elapsed = elapsedMs(started);
            if (elapsed > timeoutMs) {
                throw new IllegalStateException(phase + " exceeded timeout: " + elapsed + "ms > " + timeoutMs + "ms");
            }
            System.out.println("PHASE " + phase + " elapsedMs=" + elapsed);
            return value;
        } catch (RuntimeException ex) {
            System.out.println("PHASE " + phase + " failed: " + ex.getMessage());
            throw ex;
        }
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static PdfAssert inspectPdf(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return new PdfAssert(document.getNumberOfPages(), stripper.getText(document),
                    detectBottomBandWarnings(document));
        }
    }

    private static List<String> detectBottomBandWarnings(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        List<String> warnings = new ArrayList<>();
        for (int pageIndex = 1; pageIndex < document.getNumberOfPages() - 1; pageIndex++) {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 72);
            int width = image.getWidth();
            int height = image.getHeight();
            int x0 = Math.round(width * 0.14f);
            int x1 = Math.round(width * 0.86f);
            int y0 = Math.max(0, height - 58);
            int y1 = Math.max(y0 + 1, height - 40);
            int nonBackground = 0;
            int total = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    total++;
                    if (!isPaperBackground(image.getRGB(x, y))) {
                        nonBackground++;
                    }
                }
            }
            double ratio = total == 0 ? 0 : (double) nonBackground / total;
            if (ratio > 0.03d) {
                warnings.add("page-" + String.format(Locale.ROOT, "%02d", pageIndex + 1)
                        + ":bottomBandNonBackground=" + String.format(Locale.ROOT, "%.3f", ratio));
            }
        }
        return warnings;
    }

    private static boolean isPaperBackground(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return colorDistance(r, g, b, 254, 252, 247) < 28
                || colorDistance(r, g, b, 247, 243, 234) < 28
                || colorDistance(r, g, b, 255, 255, 255) < 24;
    }

    private static int colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    private static boolean containsPresaleText(String text) {
        return text.contains("GEO") || text.contains("售前") || text.contains("诊断报告");
    }

    private static void printComparison(List<RenderResult> results) {
        if (results.size() < 2) return;
        RenderResult first = results.get(0);
        RenderResult second = results.get(1);
        boolean reusedPass = second.elapsedMs <= Math.round(first.elapsedMs * 1.2);
        System.out.printf(Locale.ROOT,
                "BROWSER_REUSE_HOT_START firstMs=%d secondMs=%d deltaMs=%d pass=%s standard=second<=first*1.2%n",
                first.elapsedMs, second.elapsedMs, first.elapsedMs - second.elapsedMs, reusedPass);
        System.out.println("MEMORY_PEAK_MB_SEQUENCE=" + formatMemorySequence(results));
        System.out.println("MANUAL_CHECKLIST=Open both PDFs, confirm 18 visual pages, charts are visible, and each page maps to data-page-id/page-XX content.");
    }

    private static String formatMemorySequence(List<RenderResult> results) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            parts.add((i + 1) + ":" + String.format(Locale.ROOT, "%.1f",
                    results.get(i).maxPrivateBytes / 1024.0 / 1024.0));
        }
        return String.join(",", parts);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isPrintMetricsOk(String text) {
        return text.contains("pageCount=18")
                && text.contains("chartCount=5")
                && text.contains("chartsWithData=5")
                && text.contains("representativeCanvasNonBlank=true")
                && text.contains("overflowPages=[]");
    }

    private static String formatConsole(ConsoleMessage msg) {
        return "[" + msg.type() + "] " + msg.text();
    }

    private static String formatFailedRequest(Request req) {
        return req.method() + " " + req.url() + " failure=" + req.failure();
    }

    private static void dumpDebug(Path debugDir, List<String> consoleLines, List<String> networkLines) throws IOException {
        Files.write(debugDir.resolve("console.log"), consoleLines, StandardCharsets.UTF_8);
        Files.write(debugDir.resolve("network-failed.log"), networkLines, StandardCharsets.UTF_8);
    }

    private record PdfAssert(int pages, String text, List<String> bottomBandWarnings) {
    }

    private record RenderResult(String fileName, long elapsedMs, long bytes, int pages,
                                String text, long maxPrivateBytes) {
    }

    private static final class MemorySampler {
        private final long rootPid;
        private final Set<Long> baselineChromiumPids = RUN_BASELINE_CHROMIUM_PIDS;
        private volatile boolean running;
        private volatile long maxPrivateBytes;
        private Thread thread;

        private MemorySampler(long rootPid) {
            this.rootPid = rootPid;
        }

        private void start() {
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    maxPrivateBytes = Math.max(maxPrivateBytes,
                            readProcessTreePrivateBytes(rootPid, baselineChromiumPids));
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "presale-poc-memory-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        private void stop() {
            running = false;
            if (thread != null) {
                try {
                    thread.join(2_000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            maxPrivateBytes = Math.max(maxPrivateBytes,
                    readProcessTreePrivateBytes(rootPid, baselineChromiumPids));
        }

        private long maxPrivateBytes() {
            return maxPrivateBytes;
        }
    }

    private static long readProcessTreePrivateBytes(long rootPid, Set<Long> baselineChromiumPids) {
        String baseline = baselineChromiumPids.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String script = """
                $RootPid = %d
                $Baseline = @(%s)
                function Get-ChildProcessTree($pid) {
                  $children = Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -eq $pid }
                  foreach ($child in $children) {
                    $child
                    Get-ChildProcessTree $child.ProcessId
                  }
                }
                $tree = @(Get-ChildProcessTree $RootPid)
                $sum = 0
                foreach ($item in $tree) {
                  $p = Get-Process -Id $item.ProcessId -ErrorAction SilentlyContinue
                  if ($p) { $sum += $p.PrivateMemorySize64 }
                }
                if ($sum -eq 0) {
                  $chromes = Get-CimInstance Win32_Process | Where-Object {
                    ($_.Name -match 'chrome|chromium|msedge|headless_shell') -and
                    ($Baseline -notcontains $_.ProcessId)
                  }
                  foreach ($item in $chromes) {
                    $p = Get-Process -Id $item.ProcessId -ErrorAction SilentlyContinue
                    if ($p) { $sum += $p.PrivateMemorySize64 }
                  }
                }
                Write-Output $sum
                """.formatted(rootPid, baseline);
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", script).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor(5, TimeUnit.SECONDS);
            if (output.isBlank()) return 0L;
            String lastLine = output.lines().reduce((a, b) -> b).orElse("0").trim();
            return Long.parseLong(lastLine);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static Set<Long> readChromiumPids() {
        String script = """
                Get-CimInstance Win32_Process | Where-Object {
                  ($_.Name -match 'chrome|chromium|msedge|headless_shell')
                } | ForEach-Object { $_.ProcessId }
                """;
        Set<Long> pids = new HashSet<>();
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", script).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor(5, TimeUnit.SECONDS);
            output.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .forEach(line -> pids.add(Long.parseLong(line)));
        } catch (Exception ignored) {
            return Set.of();
        }
        return pids;
    }

    private static String listBrowserLikeProcesses() {
        String script = """
                Get-CimInstance Win32_Process | Where-Object {
                  ($_.Name -match 'chrome|chromium|msedge|headless_shell|node') -or
                  ($_.CommandLine -match 'playwright|ms-playwright|chromium')
                } | Select-Object ProcessId,ParentProcessId,Name,CommandLine |
                Format-List
                """;
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", script).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(5, TimeUnit.SECONDS);
            return output;
        } catch (Exception ex) {
            return "process snapshot failed: " + ex.getMessage();
        }
    }
}
