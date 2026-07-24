package com.huanjing.geo.module.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.export.render.ExportRenderKernel;
import com.huanjing.geo.module.export.render.ExportRenderProfile;
import com.huanjing.geo.module.export.render.ExportRenderRequest;
import com.huanjing.geo.module.export.render.ExportRenderResult;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.service.PresaleExportStorageService;
import com.huanjing.geo.module.project.entity.BaselineReportExport;
import com.huanjing.geo.module.project.mapper.BaselineReportExportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineReportExportWorker {
    private final PresaleExportProperties properties;
    private final BaselineReportExportMapper exportMapper;
    private final BaselineRenderTokenService renderTokenService;
    private final ExportRenderKernel renderKernel;
    private final PresaleExportStorageService storageService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${geo.report.web-base-url:http://127.0.0.1:3000}")
    private String webBaseUrl;

    @Value("${geo.report.web-fallback-base-url:http://127.0.0.1:3000}")
    private String webFallbackBaseUrl;

    @Scheduled(fixedDelayString = "${geo.baseline-export.worker.scan-interval-ms:1000}")
    public void scanAndRun() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        Optional<BaselineReportExport> claimed = claimOne();
        claimed.ifPresent(this::runClaimed);
    }

    private Optional<BaselineReportExport> claimOne() {
        return transactionTemplate.execute(status -> {
            BaselineReportExport pending = exportMapper.selectOnePendingForUpdateSkipLocked();
            if (pending == null) {
                return Optional.empty();
            }
            String workerId = "baseline-" + UUID.randomUUID();
            if (exportMapper.markClaimed(pending.getId(), workerId) <= 0) {
                return Optional.empty();
            }
            pending.setStatus(BaselineReportExportStatuses.RUNNING);
            pending.setWorkerId(workerId);
            return Optional.of(pending);
        });
    }

    private void runClaimed(BaselineReportExport task) {
        Long exportId = task.getId();
        Path workDir = Path.of(properties.getStorage().getLocalRoot(), "baseline-" + exportId);
        Path pdfPath = workDir.resolve("report.pdf");
        Path debugDir = workDir.resolve("debug");
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "baseline-export-heartbeat-" + exportId);
            t.setDaemon(true);
            return t;
        });
        BaselineRenderTokenService.TokenIssueResult token = null;
        try {
            token = issueRenderToken(task);
            heartbeat.scheduleAtFixedRate(
                    () -> exportMapper.heartbeat(exportId, task.getWorkerId()),
                    properties.getWorker().getHeartbeatIntervalMs(),
                    properties.getWorker().getHeartbeatIntervalMs(),
                    TimeUnit.MILLISECONDS);
            prepareWorkDir(workDir);
            ExportRenderResult result = renderKernel.render(ExportRenderRequest.builder()
                    .exportId(exportId)
                    .renderUrl(buildRenderUrl(token.token()))
                    .outputPath(pdfPath)
                    .debugDir(debugDir)
                    .profile(buildRenderProfile())
                    .build());
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String pdfKey = "baseline/exports/" + exportId + "/report.pdf";
            storageService.uploadPdf(pdfBytes, pdfKey);
            markSuccess(task, pdfKey, result, pdfBytes.length);
            deleteLocalWorkDirQuietly(workDir);
        } catch (Exception ex) {
            log.error("Baseline export render failed: exportId={}", exportId, ex);
            markFailed(exportId, failureMessage(ex), readMetricsJson(debugDir));
        } finally {
            heartbeat.shutdownNow();
            if (token != null) {
                renderTokenService.invalidate(token.tokenId());
            }
        }
    }

    private BaselineRenderTokenService.TokenIssueResult issueRenderToken(BaselineReportExport task) {
        Duration ttl = Duration.ofMillis(properties.getBrowser().getReadyTimeoutMs()
                + properties.getBrowser().getPdfTimeoutMs()
                + 5L * 60_000L);
        BaselineRenderTokenService.TokenIssueResult token = renderTokenService.issue(
                task.getId(), task.getProjectId(), task.getBaselineId(), ttl);
        task.setRenderTokenId(token.tokenId());
        exportMapper.updateById(task);
        return token;
    }

    private void markSuccess(BaselineReportExport task, String pdfKey, ExportRenderResult result, long fileSize) {
        task.setStatus(BaselineReportExportStatuses.SUCCESS);
        task.setFileKey(pdfKey);
        task.setFileSize(fileSize);
        task.setFilePages(extractPageCount(result.getMetricsJson()));
        task.setMetricsJson(result.getMetricsJson());
        task.setRenderTokenId(null);
        task.setUpdatedAt(LocalDateTime.now());
        exportMapper.updateById(task);
    }

    private void markFailed(Long exportId, String message, String metricsJson) {
        BaselineReportExport latest = exportMapper.selectById(exportId);
        if (latest == null) {
            return;
        }
        latest.setStatus(BaselineReportExportStatuses.FAILED);
        latest.setErrorMsg(message);
        latest.setMetricsJson(metricsJson);
        latest.setRenderTokenId(null);
        latest.setUpdatedAt(LocalDateTime.now());
        exportMapper.updateById(latest);
    }

    private int extractPageCount(String metricsJson) {
        try {
            JsonNode root = objectMapper.readTree(metricsJson);
            return root.path("page_count").asInt(1);
        } catch (Exception ex) {
            return 1;
        }
    }

    private void prepareWorkDir(Path workDir) throws Exception {
        Path parent = workDir.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createDirectories(workDir);
        Files.createDirectories(workDir.resolve("debug"));
    }

    private void deleteLocalWorkDirQuietly(Path workDir) {
        try (Stream<Path> files = Files.walk(workDir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ex) {
                    log.warn("Delete baseline export local work file failed: {}", path, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Delete baseline export local work dir failed", ex);
        }
    }

    private String buildRenderUrl(String token) {
        return resolveRenderBaseUrl() + "/baseline-print/" + token;
    }

    private String resolveRenderBaseUrl() {
        try {
            return requireResolvableBaseUrl(webBaseUrl, "geo.report.web-base-url");
        } catch (IllegalStateException primaryFailure) {
            if (webFallbackBaseUrl != null && !webFallbackBaseUrl.isBlank()) {
                try {
                    return requireResolvableBaseUrl(webFallbackBaseUrl, "geo.report.web-fallback-base-url");
                } catch (IllegalStateException fallbackFailure) {
                    primaryFailure.addSuppressed(fallbackFailure);
                }
            }
            throw primaryFailure;
        }
    }

    private String requireResolvableBaseUrl(String value, String configName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Baseline export render base url is blank: " + configName);
        }
        String baseUrl = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Baseline export render base url host is blank: " + configName);
        }
        try {
            InetAddress.getByName(host);
            return baseUrl;
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("Baseline export render host is not resolvable: " + host, ex);
        }
    }

    private ExportRenderProfile buildRenderProfile() {
        PresaleExportProperties.Browser browser = properties.getBrowser();
        return ExportRenderProfile.builder()
                .pageFormat("A4")
                .deviceScaleFactor(browser.getDeviceScaleFactor())
                .viewportWidth(browser.getViewportWidth())
                .viewportHeight(browser.getViewportHeight())
                .pageLoadTimeoutMs(browser.getPageLoadTimeoutMs())
                .readyTimeoutMs(browser.getReadyTimeoutMs())
                .pdfTimeoutMs(browser.getPdfTimeoutMs())
                .acquireTimeoutMs(browser.getAcquireTimeoutMs())
                .build();
    }

    private String readMetricsJson(Path debugDir) {
        try {
            Path metrics = debugDir.resolve("metrics.json");
            return Files.exists(metrics) ? Files.readString(metrics) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String failureMessage(Throwable ex) {
        if (ex == null) {
            return "Render failed";
        }
        return ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
}
