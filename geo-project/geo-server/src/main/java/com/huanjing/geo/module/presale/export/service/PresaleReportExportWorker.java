package com.huanjing.geo.module.presale.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.export.render.ExportRenderConcurrencyException;
import com.huanjing.geo.module.export.render.ExportRenderKernel;
import com.huanjing.geo.module.export.render.ExportRenderProfile;
import com.huanjing.geo.module.export.render.ExportRenderRequest;
import com.huanjing.geo.module.export.render.ExportRenderResult;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportExportWorker {
    private final PresaleExportProperties properties;
    private final PresaleReportExportClaimService claimService;
    private final PresaleReportExportHeartbeatService heartbeatService;
    private final ExportRenderKernel renderKernel;
    private final PresaleRenderTokenService renderTokenService;
    private final PresaleExportStorageService storageService;
    private final PresaleExportCancellationRegistry cancellationRegistry;
    private final PresaleReportExportCompletionService completionService;
    private final PresaleExportDebugPackageService debugPackageService;
    private final PresaleReportExportMapper exportMapper;
    private final ObjectMapper objectMapper;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

    @Value("${geo.report.web-base-url:http://127.0.0.1:3000}")
    private String webBaseUrl;

    @Value("${geo.report.web-fallback-base-url:http://127.0.0.1:3000}")
    private String webFallbackBaseUrl;

    @Scheduled(fixedDelayString = "${geo.presale-export.worker.scan-interval-ms:1000}")
    public void scanAndRun() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        for (int i = 0; i < Math.max(1, properties.getWorker().getClaimBatchSize()); i++) {
            Optional<PresaleReportExport> claimed = claimService.claimOne();
            if (claimed.isEmpty()) {
                return;
            }
            runClaimed(claimed.get());
        }
    }

    private void runClaimed(PresaleReportExport task) {
        Long exportId = task.getId();
        log.info("Presale export task claimed: exportId={}, workerId={}", exportId, task.getWorkerId());
        Path debugDir = null;
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presale-export-heartbeat-" + exportId);
            t.setDaemon(true);
            return t;
        });
        PresaleRenderTokenService.TokenIssueResult token = null;
        ChromiumMemorySampler memorySampler = null;
        try {
            token = issueRenderToken(task);
            heartbeat.scheduleAtFixedRate(
                    () -> heartbeatService.heartbeat(exportId),
                    properties.getWorker().getHeartbeatIntervalMs(),
                    properties.getWorker().getHeartbeatIntervalMs(),
                    TimeUnit.MILLISECONDS);

            if (cancellationRegistry.isCanceled(exportId) || isCanceledInDb(exportId)) {
                cleanupCanceled(exportId, null);
                return;
            }

            Path workDir = Path.of(properties.getStorage().getLocalRoot(), String.valueOf(exportId));
            Files.createDirectories(workDir);
            Path pdfPath = workDir.resolve("report.pdf");
            debugDir = workDir.resolve("debug");
            String renderUrl = buildRenderUrl(token.token());
            log.info("Presale export renderUrl: {}", renderUrl);

            memorySampler = new ChromiumMemorySampler(ProcessHandle.current());
            memorySampler.start();
            ExportRenderResult result;
            try {
                result = renderKernel.render(ExportRenderRequest.builder()
                        .exportId(exportId)
                        .renderUrl(renderUrl)
                        .outputPath(pdfPath)
                        .debugDir(debugDir)
                        .profile(buildRenderProfile())
                        .build());
            } finally {
                memorySampler.stop();
            }
            result = result.withMetricsJson(addMemoryMetrics(result.getMetricsJson(), memorySampler));

            if (cancellationRegistry.isCanceled(exportId) || isCanceledInDb(exportId)) {
                cleanupCanceled(exportId, pdfPath);
                return;
            }
            QualityFailure qualityFailure = validateRenderQuality(result);
            if (qualityFailure != null) {
                Files.deleteIfExists(pdfPath);
                String debugKey = debugPackageService.retainFailureDebugPackage(
                        exportId, debugDir, qualityFailure.message());
                markFailed(exportId, qualityFailure.errorCode(), qualityFailure.message(),
                        debugKey, result.getMetricsJson());
                return;
            }

            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String pdfKey = "presale/exports/" + exportId + "/report.pdf";
            storageService.uploadPdf(pdfBytes, pdfKey);
            markSuccess(exportId, pdfKey, result, pdfBytes.length);
            deleteLocalWorkDirQuietly(exportId, workDir);
        } catch (ExportRenderConcurrencyException ex) {
            requeue(exportId, ex.getMessage());
        } catch (Exception ex) {
            String debugKey = debugPackageService.retainFailureDebugPackage(exportId, debugDir, ex.getMessage());
            markFailed(exportId, "RENDER_FAILED", ex.getMessage(), debugKey, readMetricsJson(debugDir));
        } finally {
            heartbeat.shutdownNow();
            if (token != null) {
                renderTokenService.invalidate(token.tokenId());
            }
            if (memorySampler != null) {
                memorySampler.stop();
            }
            cancellationRegistry.clear(exportId);
        }
    }

    private PresaleRenderTokenService.TokenIssueResult issueRenderToken(PresaleReportExport task) {
        Duration ttl = Duration.ofMillis(properties.getBrowser().getReadyTimeoutMs()
                + properties.getBrowser().getPdfTimeoutMs()
                + 5L * 60_000L);
        PresaleRenderTokenService.TokenIssueResult token = renderTokenService.issue(
                task.getId(), task.getReportId(), task.getVersionId(), ttl);
        task.setRenderTokenId(token.tokenId());
        exportMapper.updateById(task);
        return token;
    }

    private void markSuccess(Long exportId, String pdfKey, ExportRenderResult result, long fileSize) {
        PresaleReportExport latest = exportMapper.selectById(exportId);
        if (latest == null || PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            storageService.remove(pdfKey);
            return;
        }
        boolean completed = completionService.markSuccessAndIncrementVersion(
                exportId, pdfKey, fileSize, extractPageCount(result.getMetricsJson()), result.getMetricsJson());
        if (!completed) {
            storageService.remove(pdfKey);
            return;
        }
        log.info("Presale export succeeded: exportId={}, fileKey={}, size={}", exportId, pdfKey, fileSize);
    }

    private void markFailed(Long exportId, String errorCode, String message, String debugKey, String renderMetricsJson) {
        PresaleReportExport latest = exportMapper.selectById(exportId);
        if (latest == null || PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            return;
        }
        latest.setStatus(PresaleExportStatuses.FAILED);
        latest.setErrorMsg(message == null ? "Render failed" : message);
        latest.setRenderTokenId(null);
        if (renderMetricsJson != null) {
            latest.setMetricsJson(metricsJsonHelper.mergeRenderMetrics(latest.getMetricsJson(), renderMetricsJson));
        }
        latest.setMetricsJson(metricsJsonHelper.appendRetryHistory(latest.getMetricsJson(),
                PresaleExportMetricsJsonHelper.RetryHistoryEntry.builder()
                        .errorCode(errorCode)
                        .errorMsg(latest.getErrorMsg())
                        .retryCount(latest.getRetryCount())
                        .build()));
        if (debugKey != null) {
            latest.setMetricsJson(metricsJsonHelper.putString(latest.getMetricsJson(), "debug_key", debugKey));
        }
        latest.setUpdatedAt(LocalDateTime.now());
        exportMapper.updateById(latest);
        log.warn("Presale export failed: exportId={}, error={}", exportId, latest.getErrorMsg());
    }

    private void requeue(Long exportId, String message) {
        PresaleReportExport latest = exportMapper.selectById(exportId);
        if (latest == null || PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            return;
        }
        latest.setStatus(PresaleExportStatuses.PENDING);
        latest.setErrorMsg(null);
        latest.setWorkerId(null);
        latest.setRenderTokenId(null);
        latest.setMetricsJson(metricsJsonHelper.appendRetryHistory(latest.getMetricsJson(),
                PresaleExportMetricsJsonHelper.RetryHistoryEntry.builder()
                        .errorCode("CONCURRENCY_REQUEUE")
                        .errorMsg(message)
                        .retryCount(latest.getRetryCount())
                        .build()));
        latest.setUpdatedAt(LocalDateTime.now());
        exportMapper.updateById(latest);
        log.info("Presale export requeued after concurrency timeout: exportId={}", exportId);
    }

    private boolean isCanceledInDb(Long exportId) {
        PresaleReportExport latest = exportMapper.selectById(exportId);
        return latest != null && PresaleExportStatuses.CANCELED.equals(latest.getStatus());
    }

    private void cleanupCanceled(Long exportId, Path pdfPath) {
        try {
            if (pdfPath != null) {
                Files.deleteIfExists(pdfPath);
            }
        } catch (Exception ex) {
            log.warn("Delete canceled presale export temp PDF failed, exportId={}", exportId, ex);
        }
        PresaleReportExport latest = exportMapper.selectById(exportId);
        if (latest != null && PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            latest.setRenderTokenId(null);
            latest.setUpdatedAt(LocalDateTime.now());
            exportMapper.updateById(latest);
        }
        log.info("Presale export canceled, worker cleanup completed: exportId={}", exportId);
    }

    private void deleteLocalWorkDirQuietly(Long exportId, Path workDir) {
        try (Stream<Path> files = Files.walk(workDir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ex) {
                    log.warn("Delete presale export local work file failed, exportId={}, path={}", exportId, path, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Delete presale export local work dir failed, exportId={}", exportId, ex);
        }
    }

    private int extractPageCount(String metricsJson) {
        try {
            JsonNode root = objectMapper.readTree(metricsJson);
            JsonNode pageCount = root.get("page_count");
            return pageCount == null ? 19 : pageCount.asInt(19);
        } catch (Exception ex) {
            return 19;
        }
    }

    private QualityFailure validateRenderQuality(ExportRenderResult result) {
        try {
            JsonNode root = objectMapper.readTree(result.getMetricsJson());
            int pageCount = root.path("page_count").asInt(19);
            if (properties.getQuality().isEnforcePageCount() && pageCount != 19) {
                return new QualityFailure("PRINT_PAGE_COUNT_MISMATCH",
                        "Print page count mismatch: expected 19, actual " + pageCount);
            }

            boolean bottomBandOk = root.path("bottom_band_ok").asBoolean(true);
            JsonNode overflowPages = root.path("overflow_pages");
            boolean hasBlockingOverflow = overflowPages.isArray() ? !overflowPages.isEmpty() : !bottomBandOk;
            if (properties.getQuality().isEnforceBottomBand() && !bottomBandOk && hasBlockingOverflow) {
                return new QualityFailure("PRINT_BOTTOM_BAND_BLOCKED",
                        "Print bottom safety band blocked");
            }
            if (!bottomBandOk && overflowPages.isArray() && overflowPages.isEmpty()) {
                log.warn("Presale print bottom_band_ok=false but overflow_pages is empty; export continues");
            }

            JsonNode contentOverflows = root.path("content_overflows");
            if (contentOverflows.isArray() && !contentOverflows.isEmpty()) {
                JsonNode first = contentOverflows.get(0);
                String block = first.path("block").asText("未知区域");
                String field = first.path("field").asText("");
                int overflowPx = first.path("overflowPx").asInt(0);
                String message = "AI 搜索新战场 · " + block + " 内容过长，请缩短后重试导出";
                if (!field.isBlank()) {
                    message = message + " (" + field + ", overflow " + overflowPx + "px)";
                }
                return new QualityFailure("PRINT_CONTENT_OVERFLOW", message);
            }

            boolean canvasNonBlank = root.path("canvas_non_blank").asBoolean(true);
            if (!canvasNonBlank) {
                String message = "Presale print chart canvas is blank";
                if (properties.getQuality().isEnforceChartNonBlank()) {
                    return new QualityFailure("PRINT_CHART_BLANK", message);
                }
                log.warn("{}; export continues because enforce-chart-non-blank=false", message);
            }
            return null;
        } catch (Exception ex) {
            return new QualityFailure("PRINT_METRICS_INVALID", "Print metrics invalid: " + ex.getMessage());
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildRenderUrl(String token) {
        return resolveRenderBaseUrl() + "/presale-print/" + token;
    }

    private String resolveRenderBaseUrl() {
        try {
            return requireResolvableBaseUrl(webBaseUrl, "geo.report.web-base-url");
        } catch (IllegalStateException primaryFailure) {
            if (hasText(webFallbackBaseUrl)) {
                try {
                    String fallbackBaseUrl = requireResolvableBaseUrl(
                            webFallbackBaseUrl, "geo.report.web-fallback-base-url");
                    log.warn("Presale export render base url is unavailable, fallback applied: primary={}, fallback={}",
                            webBaseUrl, fallbackBaseUrl);
                    return fallbackBaseUrl;
                } catch (IllegalStateException fallbackFailure) {
                    primaryFailure.addSuppressed(fallbackFailure);
                }
            }
            throw primaryFailure;
        }
    }

    private String requireResolvableBaseUrl(String value, String configName) {
        if (!hasText(value)) {
            throw new IllegalStateException("Presale export render base url is blank: " + configName);
        }
        String baseUrl = trimTrailingSlash(value.trim());
        URI uri = parseRenderBaseUri(baseUrl, configName);
        String host = uri.getHost();
        if (!hasText(host)) {
            throw new IllegalStateException("Presale export render base url host is blank: "
                    + configName + "=" + baseUrl);
        }
        try {
            InetAddress.getByName(host);
            return baseUrl;
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("Presale export render host is not resolvable: "
                    + host + ". Configure REPORT_WEB_BASE_URL to an address reachable from the geo-server process; "
                    + "Docker Compose can use http://geo-web, local deployments usually use http://127.0.0.1:3000.",
                    ex);
        }
    }

    private URI parseRenderBaseUri(String baseUrl, String configName) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalStateException("Presale export render base url must use http or https: "
                        + configName + "=" + baseUrl);
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Presale export render base url is invalid: "
                    + configName + "=" + baseUrl, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
        if (debugDir == null) {
            return null;
        }
        try {
            Path metrics = debugDir.resolve("metrics.json");
            return Files.exists(metrics) ? Files.readString(metrics) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String addMemoryMetrics(String metricsJson, ChromiumMemorySampler sampler) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(metricsJson);
            root.put("memory_peak_mb_approx", sampler.getPeakMb());
            root.put("memory_sampling_method", "process_handle");
            root.put("memory_sample_count", sampler.getSampleCount());
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return metricsJson;
        }
    }

    private record QualityFailure(String errorCode, String message) {
    }

}
