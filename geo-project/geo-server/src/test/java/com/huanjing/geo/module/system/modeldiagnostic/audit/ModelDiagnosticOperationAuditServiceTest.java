package com.huanjing.geo.module.system.modeldiagnostic.audit;

import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ModelDiagnosticOperationAuditServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void terminalAuditContainsOnlySafeOperationalMetadata() {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        ModelDiagnosticOperationAuditService service =
                new ModelDiagnosticOperationAuditService(activityLogService);
        AiModelDiagnosticRun run = terminalRun();

        service.recordTerminal(run);

        ArgumentCaptor<Object> extraCaptor = ArgumentCaptor.forClass(Object.class);
        verify(activityLogService).logAction(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(ModelDiagnosticOperationAuditService.ACTION),
                org.mockito.ArgumentMatchers.eq(ModelDiagnosticOperationAuditService.TARGET_TYPE),
                org.mockito.ArgumentMatchers.eq(9L),
                isNull(), isNull(), extraCaptor.capture());
        Map<String, Object> extra = (Map<String, Object>) extraCaptor.getValue();
        assertTrue(extra.keySet().containsAll(java.util.Set.of(
                "platformConfigId", "diagnosticMode", "testMode", "status",
                "conclusion", "durationMs", "errorCategory", "errorCode")));
        assertFalse(extra.containsKey("userMessage"));
        assertFalse(extra.containsKey("systemPrompt"));
        assertFalse(extra.containsKey("answer"));
        assertFalse(extra.containsKey("sanitizedRequest"));
        assertFalse(extra.containsKey("sanitizedResponse"));
        assertFalse(extra.containsKey("configSnapshotJson"));
    }

    @Test
    void runningOrMalformedRecordsDoNotCreateAuditNoise() {
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        ModelDiagnosticOperationAuditService service =
                new ModelDiagnosticOperationAuditService(activityLogService);
        AiModelDiagnosticRun run = terminalRun();
        run.setStatus("RUNNING");

        service.recordTerminal(run);
        run.setStatus("UNKNOWN");
        service.recordTerminal(run);

        verify(activityLogService, never()).logAction(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private AiModelDiagnosticRun terminalRun() {
        AiModelDiagnosticRun run = new AiModelDiagnosticRun();
        run.setId(9L);
        run.setOperatorId(42L);
        run.setPlatformConfigId(3L);
        run.setDiagnosticMode("WEB_SEARCH");
        run.setTestMode("STANDARD_PROBE");
        run.setStatus("SUCCEEDED");
        run.setConclusion("PASS");
        run.setDurationMs(1_234L);
        run.setErrorCategory(null);
        run.setErrorCode(null);
        run.setUserMessage("secret prompt");
        run.setAnswer("secret answer");
        run.setSanitizedRequest("request body");
        run.setSanitizedResponse("response body");
        run.setConfigSnapshotJson("credential snapshot");
        return run;
    }
}
