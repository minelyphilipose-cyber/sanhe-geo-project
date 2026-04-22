package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Presale generation orchestrator.
 * mockEnabled=true: fixture flow.
 * mockEnabled=false: PR-3 real pipeline (stage C skeleton in this commit).
 */
@Component
public class PresaleGenerateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PresaleGenerateOrchestrator.class);
    private static final int FAILURE_REASON_MAX_LEN = 500;

    private static final String STAGE_BATCH1 = "BATCH1";
    private static final String STAGE_COMPETITOR_EXTRACT = "COMPETITOR_EXTRACT";
    private static final String STAGE_BATCH2 = "BATCH2";
    private static final String STAGE_L1_AGGREGATE = "L1_AGGREGATE";
    private static final String STAGE_L2_COMPUTE = "L2_COMPUTE";
    private static final String STAGE_L3_INIT = "L3_INIT";

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportMapper reportMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    private final PresaleL3InitService l3InitService;
    private final ObjectMapper objectMapper;

    @Value("${presale.generate.mock:true}")
    private boolean mockEnabled;

    @Value("${presale.generate.mock-delay-ms:5000}")
    private long mockDelayMs;

    @Value("${presale.generate.mock-fixture-path:fixtures/01-mock-sample-v1.2.json}")
    private String mockFixturePath;

    @Value("${presale.generate.allow-synthetic-fallback.mock:true}")
    private boolean allowSyntheticFallbackMock;

    @Value("${presale.generate.allow-synthetic-fallback.real:false}")
    private boolean allowSyntheticFallbackReal;

    public PresaleGenerateOrchestrator(PresaleReportVersionMapper versionMapper,
                                       PresaleReportMapper reportMapper,
                                       AiPlatformConfigMapper aiPlatformConfigMapper,
                                       PresaleAiPromptResultMapper aiPromptResultMapper,
                                       PresaleComputedSnapshotEnricher computedSnapshotEnricher,
                                       PresaleL3InitService l3InitService,
                                       ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.reportMapper = reportMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.computedSnapshotEnricher = computedSnapshotEnricher;
        this.l3InitService = l3InitService;
        this.objectMapper = objectMapper;
    }

    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
        try {
            doTriggerGenerate(versionId, operatorUserId, isManager);
        } catch (Throwable t) {
            log.error("Presale generate fatal error, versionId={}", versionId, t);
            try {
                markFailed(versionId, truncateReason("Unexpected error: " + t.getClass().getSimpleName()));
            } catch (Throwable markFailedError) {
                log.error("Failed to mark presale version FAILED after fatal error, versionId={}", versionId, markFailedError);
            }
        }
    }

    private void doTriggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
        if (mockEnabled) {
            runMockFlow(versionId);
            return;
        }
        runRealSkeletonFlow(versionId, operatorUserId, isManager);
    }

    private void runMockFlow(Long versionId) {
        log.info("Presale mock generate start, versionId={}, delay={}ms", versionId, mockDelayMs);
        markRunningForMock(versionId);

        try {
            Thread.sleep(mockDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(versionId, truncateReason("Generation interrupted"));
            return;
        }

        String rawJson;
        String computedJson;
        String editableJson;
        try {
            FixturePayload payload = loadFixturePayload();
            rawJson = payload.rawJson();
            computedJson = computedSnapshotEnricher.enrichAndValidate(
                    versionId, rawJson, payload.computedJson(), resolveAllowSyntheticFallback()
            );
            editableJson = l3InitService.derive(rawJson, computedJson);
        } catch (Exception e) {
            log.error("Failed to build presale snapshot, fixturePath={}", mockFixturePath, e);
            markFailed(versionId, truncateReason("Snapshot build failed: " + e.getMessage()));
            return;
        }

        PresaleReportVersion current = versionMapper.selectById(versionId);
        int totalCalls = current == null || current.getTotalLlmCalls() == null
                ? 0 : current.getTotalLlmCalls();

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
        update.setGenerationStage(null);
        update.setCompletedLlmCalls(totalCalls);
        update.setTotalLlmCalls(totalCalls);
        update.setBatch1CompletedCalls(current == null ? null : current.getBatch1TotalCalls());
        update.setBatch2CompletedCalls(current == null ? null : current.getBatch2TotalCalls());
        update.setIsDegraded(false);
        update.setRawSnapshotJson(rawJson);
        update.setComputedSnapshotJson(computedJson);
        update.setEditableContentJson(editableJson);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);

        log.info("Presale mock generate done, versionId={}", versionId);
    }

    private void runRealSkeletonFlow(Long versionId, Long operatorUserId, boolean isManager) {
        PreflightResult preflight = preflight(versionId);
        if (!preflight.success()) {
            markFailed(versionId, truncateReason("CONFIG_MISSING: " + preflight.failureReason()));
            return;
        }

        markRunning(versionId, preflight.totalUpperBoundCalls(), preflight.batch1TotalCalls());

        // Stage C skeleton only: keep stage transitions and logs.
        enterStage(versionId, STAGE_BATCH1, "skeleton only");
        enterStage(versionId, STAGE_COMPETITOR_EXTRACT, "skeleton only");

        int extractedCompetitorCount = 0;
        int batch2TotalCalls = preflight.platformCount() * preflight.competitorPromptCount() * extractedCompetitorCount * 2;
        updateAfterCompetitorExtract(
                versionId,
                extractedCompetitorCount,
                batch2TotalCalls,
                preflight.batch1TotalCalls() + batch2TotalCalls
        );

        if (extractedCompetitorCount > 0) {
            enterStage(versionId, STAGE_BATCH2, "skeleton only");
        } else {
            log.info("PR-3 stage C skeleton skip batch2 because extracted competitors is 0, versionId={}", versionId);
        }

        enterStage(versionId, STAGE_L1_AGGREGATE, "skeleton only");
        enterStage(versionId, STAGE_L2_COMPUTE, "skeleton only");
        enterStage(versionId, STAGE_L3_INIT, "skeleton only");

        markFailed(versionId, "PR-3 stage C skeleton: real pipeline business not implemented yet");
        log.info("PR-3 stage C skeleton finished with placeholder failure, versionId={}, operatorUserId={}, isManager={}",
                versionId, operatorUserId, isManager);
    }

    private PreflightResult preflight(Long versionId) {
        PresaleReportVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            return PreflightResult.fail("version not found: " + versionId);
        }
        PresaleReport report = reportMapper.selectById(version.getReportId());
        if (report == null) {
            return PreflightResult.fail("report not found: " + version.getReportId());
        }
        if (report.getBrandName() == null || report.getBrandName().isBlank()) {
            return PreflightResult.fail("report.brand_name is blank");
        }

        int platformCount = countEnabledPlatforms();
        if (platformCount < 1) {
            return PreflightResult.fail("enabled platform count is 0");
        }

        int genericPromptCount = countPromptTemplates(0);
        if (genericPromptCount < 1) {
            return PreflightResult.fail("generic prompt count is 0");
        }

        int competitorPromptCount = countPromptTemplates(1);
        int batch1TotalCalls = platformCount * genericPromptCount * 2;
        int totalUpperBoundCalls = batch1TotalCalls + (platformCount * competitorPromptCount * 3 * 2);
        return PreflightResult.success(platformCount, competitorPromptCount, batch1TotalCalls, totalUpperBoundCalls);
    }

    private void markRunningForMock(Long versionId) {
        PresaleReportVersion current = versionMapper.selectById(versionId);
        int totalCalls = current == null || current.getTotalLlmCalls() == null
                ? 0 : current.getTotalLlmCalls();

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        update.setGenerationStage(STAGE_BATCH1);
        update.setCompletedLlmCalls(0);
        update.setTotalLlmCalls(totalCalls);
        update.setBatch1CompletedCalls(0);
        update.setBatch2CompletedCalls(0);
        update.setFailureReason(null);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    private void markRunning(Long versionId, int totalLlmCalls, int batch1TotalCalls) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        update.setGenerationStage(STAGE_BATCH1);
        update.setCompletedLlmCalls(0);
        update.setTotalLlmCalls(totalLlmCalls);
        update.setBatch1TotalCalls(batch1TotalCalls);
        update.setBatch1CompletedCalls(0);
        update.setBatch2TotalCalls(null);
        update.setBatch2CompletedCalls(0);
        update.setExtractedCompetitorCount(null);
        update.setFailureReason(null);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    private void enterStage(Long versionId, String stage, String note) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStage(stage);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
        log.info("Presale generation stage entered, versionId={}, stage={}, note={}", versionId, stage, note);
    }

    private void updateAfterCompetitorExtract(Long versionId,
                                              int extractedCompetitorCount,
                                              int batch2TotalCalls,
                                              int totalCalls) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setExtractedCompetitorCount(extractedCompetitorCount);
        update.setBatch2TotalCalls(batch2TotalCalls);
        update.setTotalLlmCalls(totalCalls);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    private void markFailed(Long versionId, String reason) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        update.setGenerationStage(null);
        update.setFailureReason(truncateReason(reason));
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId) {
        triggerGenerate(versionId, null, false);
    }

    private FixturePayload loadFixturePayload() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(mockFixturePath)) {
            if (is == null) {
                throw new IOException("Mock fixture not found on classpath: " + mockFixturePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }

            JsonNode root = objectMapper.readTree(content);
            JsonNode effective = (root != null && root.has("input") && root.get("input").isObject())
                    ? root.get("input")
                    : root;

            JsonNode rawNode = (effective != null && effective.has("raw") && effective.get("raw").isObject())
                    ? effective.get("raw")
                    : effective;
            String rawJson = objectMapper.writeValueAsString(rawNode);

            JsonNode computedNode = (effective != null && effective.has("computed") && effective.get("computed").isObject())
                    ? effective.get("computed")
                    : objectMapper.createObjectNode();
            String computedJson = objectMapper.writeValueAsString(computedNode);

            JsonNode editableNode = (effective != null && effective.has("editable") && effective.get("editable").isObject())
                    ? effective.get("editable")
                    : objectMapper.createObjectNode();
            String editableJson = objectMapper.writeValueAsString(editableNode);

            return new FixturePayload(rawJson, computedJson, editableJson);
        }
    }

    private int countEnabledPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(
                new LambdaQueryWrapper<AiPlatformConfig>().eq(AiPlatformConfig::getEnabled, true)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(int hasCompetitorVar) {
        List<PromptTemplateIntentStatRow> stats = aiPromptResultMapper.selectTemplateIntentStats();
        if (stats == null || stats.isEmpty()) {
            return 0;
        }
        return stats.stream()
                .filter(row -> row != null
                        && row.getHasCompetitorVar() != null
                        && row.getHasCompetitorVar() == hasCompetitorVar)
                .mapToInt(row -> row.getTemplateCount() == null ? 0 : row.getTemplateCount())
                .sum();
    }

    private boolean resolveAllowSyntheticFallback() {
        return mockEnabled ? allowSyntheticFallbackMock : allowSyntheticFallbackReal;
    }

    private record FixturePayload(String rawJson, String computedJson, String editableJson) {
    }

    private static final class PreflightResult {
        private final boolean success;
        private final String failureReason;
        private final int platformCount;
        private final int competitorPromptCount;
        private final int batch1TotalCalls;
        private final int totalUpperBoundCalls;

        private PreflightResult(boolean success,
                                String failureReason,
                                int platformCount,
                                int competitorPromptCount,
                                int batch1TotalCalls,
                                int totalUpperBoundCalls) {
            this.success = success;
            this.failureReason = failureReason;
            this.platformCount = platformCount;
            this.competitorPromptCount = competitorPromptCount;
            this.batch1TotalCalls = batch1TotalCalls;
            this.totalUpperBoundCalls = totalUpperBoundCalls;
        }

        static PreflightResult fail(String reason) {
            return new PreflightResult(false, reason, 0, 0, 0, 0);
        }

        static PreflightResult success(int platformCount,
                                       int competitorPromptCount,
                                       int batch1TotalCalls,
                                       int totalUpperBoundCalls) {
            return new PreflightResult(true, null, platformCount, competitorPromptCount,
                    batch1TotalCalls, totalUpperBoundCalls);
        }

        boolean success() {
            return success;
        }

        String failureReason() {
            return failureReason;
        }

        int platformCount() {
            return platformCount;
        }

        int competitorPromptCount() {
            return competitorPromptCount;
        }

        int batch1TotalCalls() {
            return batch1TotalCalls;
        }

        int totalUpperBoundCalls() {
            return totalUpperBoundCalls;
        }
    }

    private String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.length() <= FAILURE_REASON_MAX_LEN) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LEN);
    }
}
