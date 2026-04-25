package com.huanjing.geo.module.presale.export.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import com.huanjing.geo.module.presale.export.render.PresaleExportConcurrencyException;
import com.huanjing.geo.module.presale.export.render.PresalePdfRenderKernel;
import com.huanjing.geo.module.presale.export.render.PresalePdfRenderRequest;
import com.huanjing.geo.module.presale.export.render.PresalePdfRenderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportExportWorker {
    private final PresaleExportProperties properties;
    private final PresaleReportExportClaimService claimService;
    private final PresaleReportExportHeartbeatService heartbeatService;
    private final PresalePdfRenderKernel renderKernel;
    private final PresaleRenderTokenService renderTokenService;
    private final PresaleExportStorageService storageService;
    private final PresaleExportCancellationRegistry cancellationRegistry;
    private final PresaleReportExportCompletionService completionService;
    private final PresaleReportExportMapper exportMapper;
    private final ObjectMapper objectMapper;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

    @Value("${geo.report.web-base-url:http://127.0.0.1:3000}")
    private String webBaseUrl;

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
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presale-export-heartbeat-" + exportId);
            t.setDaemon(true);
            return t;
        });
        PresaleRenderTokenService.TokenIssueResult token = null;
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
            Path debugDir = workDir.resolve("debug");
            String renderUrl = trimTrailingSlash(webBaseUrl) + "/presale-print/" + token.token();

            PresalePdfRenderResult result = renderKernel.render(PresalePdfRenderRequest.builder()
                    .exportId(exportId)
                    .renderUrl(renderUrl)
                    .pdfPath(pdfPath)
                    .debugDir(debugDir)
                    .build());

            if (cancellationRegistry.isCanceled(exportId) || isCanceledInDb(exportId)) {
                cleanupCanceled(exportId, pdfPath);
                return;
            }

            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String pdfKey = "presale/exports/" + exportId + "/report.pdf";
            storageService.uploadPdf(pdfBytes, pdfKey);
            markSuccess(exportId, pdfKey, result, pdfBytes.length);
        } catch (PresaleExportConcurrencyException ex) {
            requeue(exportId, ex.getMessage());
        } catch (Exception ex) {
            markFailed(exportId, ex.getMessage());
        } finally {
            heartbeat.shutdownNow();
            if (token != null) {
                renderTokenService.invalidate(token.tokenId());
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

    private void markSuccess(Long exportId, String pdfKey, PresalePdfRenderResult result, long fileSize) {
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

    private void markFailed(Long exportId, String message) {
        PresaleReportExport latest = exportMapper.selectById(exportId);
        if (latest == null || PresaleExportStatuses.CANCELED.equals(latest.getStatus())) {
            return;
        }
        latest.setStatus(PresaleExportStatuses.FAILED);
        latest.setErrorMsg(message == null ? "Render failed" : message);
        latest.setRenderTokenId(null);
        latest.setMetricsJson(metricsJsonHelper.appendRetryHistory(latest.getMetricsJson(),
                PresaleExportMetricsJsonHelper.RetryHistoryEntry.builder()
                        .errorCode("RENDER_FAILED")
                        .errorMsg(latest.getErrorMsg())
                        .retryCount(latest.getRetryCount())
                        .build()));
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
        log.info("Presale export canceled, worker cleanup completed: exportId={}", exportId);
    }

    private int extractPageCount(String metricsJson) {
        try {
            JsonNode root = objectMapper.readTree(metricsJson);
            JsonNode pageCount = root.get("pageCount");
            return pageCount == null ? 18 : pageCount.asInt(18);
        } catch (Exception ex) {
            return 18;
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
