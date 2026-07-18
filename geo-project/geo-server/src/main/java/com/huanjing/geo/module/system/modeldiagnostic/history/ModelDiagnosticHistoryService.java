package com.huanjing.geo.module.system.modeldiagnostic.history;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.modeldiagnostic.ModelDiagnosticPermissions;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUserService currentUserService;
    private final AiModelDiagnosticRunMapper runMapper;
    private final AiModelDiagnosticSessionMapper sessionMapper;

    public ModelDiagnosticHistoryPage page(ModelDiagnosticHistoryQuery query) {
        ModelDiagnosticHistoryQuery normalized = normalize(query);
        Long operatorId = requireOperatorId();
        long offset = (long) (normalized.page() - 1) * normalized.size();
        long total = runMapper.countOwnedHistory(operatorId, normalized);
        List<ModelDiagnosticRunSummary> records = total == 0 ? List.of()
                : runMapper.selectOwnedHistory(
                        operatorId, normalized, offset, normalized.size());
        return new ModelDiagnosticHistoryPage(
                records, total, normalized.page(), normalized.size());
    }

    public AiModelDiagnosticRun detail(Long runId) {
        if (runId == null || runId < 1) {
            throw new BizException(400, "Invalid diagnostic run id", 400, null);
        }
        AiModelDiagnosticRun run = runMapper.selectOwnedRun(runId, requireOperatorId());
        if (run == null) {
            throw new BizException(404, "Diagnostic run not found", 404, null);
        }
        return run;
    }

    public List<AiModelDiagnosticRun> sessionRuns(String sessionId) {
        String normalizedSessionId = normalizeUuid(sessionId);
        Long operatorId = requireOperatorId();
        if (sessionMapper.selectOwned(operatorId, normalizedSessionId) == null) {
            throw new BizException(404, "Diagnostic session not found", 404, null);
        }
        return List.copyOf(runMapper.selectOwnedSessionRuns(
                operatorId, normalizedSessionId));
    }

    private Long requireOperatorId() {
        currentUserService.ensurePermission(ModelDiagnosticPermissions.DIAGNOSE);
        SysUser operator = currentUserService.requireCurrentUser();
        return operator.getId();
    }

    private ModelDiagnosticHistoryQuery normalize(ModelDiagnosticHistoryQuery query) {
        if (query == null) {
            return new ModelDiagnosticHistoryQuery(
                    1, 20, null, null, null, null, null, null, null);
        }
        int page = Math.max(1, query.page());
        int size = Math.max(1, Math.min(query.size(), MAX_PAGE_SIZE));
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw new BizException(400, "createdFrom must not be after createdTo", 400, null);
        }
        return new ModelDiagnosticHistoryQuery(
                page, size, query.platformConfigId(), trim(query.requestedModelId()),
                trim(query.diagnosticMode()), trim(query.status()), trim(query.conclusion()),
                query.createdFrom(), query.createdTo());
    }

    private String normalizeUuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "Invalid diagnostic session id", 400, null);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
