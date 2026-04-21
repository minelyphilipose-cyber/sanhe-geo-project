package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 生成编排器。v1 仅提供 mock 实现:异步延迟后直接将 fixture 写入版本记录。
 *
 * <p>配置驱动:</p>
 * <ul>
 *   <li>{@code presale.generate.mock=true} - 开启 mock 模式(默认 true)</li>
 *   <li>{@code presale.generate.mock-delay-ms=5000} - 模拟生成耗时</li>
 * </ul>
 *
 * <p>P2 接入真 LLM 时,保留此接口签名,新增 {@code PresaleGenerateRealOrchestrator}
 * 实现,通过 @ConditionalOnProperty 切换。</p>
 */
@Component
public class PresaleGenerateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PresaleGenerateOrchestrator.class);

    private final PresaleReportVersionMapper versionMapper;

    @Value("${presale.generate.mock:true}")
    private boolean mockEnabled;

    @Value("${presale.generate.mock-delay-ms:5000}")
    private long mockDelayMs;

    /** fixture classpath 路径,随 P1·B 交付物一起部署。 */
    @Value("${presale.generate.mock-fixture-path:fixtures/01-mock-sample-v1.2.json}")
    private String mockFixturePath;

    public PresaleGenerateOrchestrator(PresaleReportVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    /**
     * 触发版本生成。异步执行,立即返回。
     *
     * <p>调用方(Service)应先将 version 以 INIT 状态入库,传 id 进来。</p>
     */
    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId) {
        if (!mockEnabled) {
            log.error("Real LLM generation is not implemented in P1·F·1·a. " +
                    "Set presale.generate.mock=true for development.");
            markFailed(versionId, "Real generation backend not yet available");
            return;
        }

        log.info("Presale mock generate start, versionId={}, delay={}ms", versionId, mockDelayMs);
        markRunning(versionId);

        try {
            // 模拟生成耗时
            Thread.sleep(mockDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(versionId, "Generation interrupted");
            return;
        }

        String rawJson;
        String computedJson;
        String editableJson;
        try {
            // 从 classpath 加载 fixture 作为 mock 结果
            // v1 简化处理:整个 fixture 塞入 raw_snapshot_json,computed/editable 留空
            // 真实场景:fixture 应已是 v1.2 merged_view 结构,Service 需拆成三层
            rawJson = loadFixture();
            computedJson = "{}";
            editableJson = "{}";
        } catch (IOException e) {
            log.error("Failed to load mock fixture: {}", mockFixturePath, e);
            markFailed(versionId, "Mock fixture load failed: " + e.getMessage());
            return;
        }

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
        update.setCompletedLlmCalls(660);
        update.setTotalLlmCalls(660);
        update.setIsDegraded(false);
        update.setRawSnapshotJson(rawJson);
        update.setComputedSnapshotJson(computedJson);
        update.setEditableContentJson(editableJson);
        versionMapper.updateById(update);

        log.info("Presale mock generate done, versionId={}", versionId);
    }

    private void markRunning(Long versionId) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        update.setCompletedLlmCalls(0);
        update.setTotalLlmCalls(660);
        versionMapper.updateById(update);
    }

    private void markFailed(Long versionId, String reason) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        update.setFailureReason(reason);
        versionMapper.updateById(update);
    }

    private String loadFixture() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(mockFixturePath)) {
            if (is == null) {
                throw new IOException("Mock fixture not found on classpath: " + mockFixturePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
