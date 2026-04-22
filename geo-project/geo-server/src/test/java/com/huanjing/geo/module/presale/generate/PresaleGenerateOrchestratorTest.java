package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleGenerateOrchestratorTest {

    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    @Mock
    private PresaleL3InitService l3InitService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PresaleGenerateOrchestrator orchestrator;

    @Test
    void triggerGenerate_realModePreflightFail_marksFailedWithoutRunning() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(9001L);
        version.setReportId(8001L);
        when(versionMapper.selectById(9001L)).thenReturn(version);

        PresaleReport report = new PresaleReport();
        report.setId(8001L);
        report.setBrandName("Acme");
        when(reportMapper.selectById(8001L)).thenReturn(report);

        when(aiPlatformConfigMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        orchestrator.triggerGenerate(9001L, 1001L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper).updateById(updateCaptor.capture());
        PresaleReportVersion update = updateCaptor.getValue();
        assertEquals(PresaleGenerateStatus.FAILED.name(), update.getGenerationStatus());
        assertNull(update.getGenerationStage());
        assertTrue(update.getFailureReason().startsWith("CONFIG_MISSING:"));
    }
}
