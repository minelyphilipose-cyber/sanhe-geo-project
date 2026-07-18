package com.huanjing.geo.module.system.modeldiagnostic.audit;

import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticRunStatus;
import com.huanjing.geo.module.system.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticOperationAuditService {

    public static final String ACTION = "AI_MODEL_DIAGNOSTIC_RUN";
    public static final String TARGET_TYPE = "ai_model_diagnostic_run";

    private final ActivityLogService activityLogService;

    public void recordTerminal(AiModelDiagnosticRun run) {
        if (run == null || run.getId() == null || run.getOperatorId() == null
                || !isTerminal(run.getStatus())) {
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("platformConfigId", run.getPlatformConfigId());
        extra.put("diagnosticMode", run.getDiagnosticMode());
        extra.put("testMode", run.getTestMode());
        extra.put("status", run.getStatus());
        extra.put("conclusion", run.getConclusion());
        extra.put("durationMs", run.getDurationMs());
        extra.put("errorCategory", run.getErrorCategory());
        extra.put("errorCode", run.getErrorCode());
        activityLogService.logAction(
                run.getOperatorId(), ACTION, TARGET_TYPE, run.getId(),
                null, null, extra);
    }

    private boolean isTerminal(String status) {
        try {
            return ModelDiagnosticRunStatus.valueOf(status).terminal();
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }
}
