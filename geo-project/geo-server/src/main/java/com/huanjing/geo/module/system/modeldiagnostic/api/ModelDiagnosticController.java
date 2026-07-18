package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.ModelDiagnosticPermissions;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryQuery;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/model-diagnostics")
@RequiredArgsConstructor
public class ModelDiagnosticController {

    private final ModelDiagnosticPlatformCatalogService platformCatalogService;
    private final ModelDiagnosticProbeCatalog probeCatalog;
    private final ModelDiagnosticApiService apiService;
    private final ModelDiagnosticHistoryService historyService;
    private final ModelDiagnosticRunViewMapper viewMapper;
    private final CurrentUserService currentUserService;

    @GetMapping("/platforms")
    public R<List<ModelDiagnosticPlatformOption>> platforms() {
        return R.ok(platformCatalogService.list());
    }

    @GetMapping("/probes")
    public R<List<ModelDiagnosticProbeOption>> probes() {
        currentUserService.ensurePermission(ModelDiagnosticPermissions.DIAGNOSE);
        return R.ok(probeCatalog.options());
    }

    @PostMapping("/runs")
    public R<ModelDiagnosticRunView> execute(
            @Valid @RequestBody ModelDiagnosticRunRequest request) {
        return R.ok(apiService.execute(request));
    }

    @GetMapping("/runs")
    public R<ModelDiagnosticHistoryPageView> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long platformConfigId,
            @RequestParam(required = false) String requestedModelId,
            @RequestParam(required = false) String diagnosticMode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        return R.ok(ModelDiagnosticHistoryPageView.from(
                historyService.page(new ModelDiagnosticHistoryQuery(
                        page, size, platformConfigId, requestedModelId, diagnosticMode,
                        status, conclusion, createdFrom, createdTo))));
    }

    @GetMapping("/runs/{id}")
    public R<ModelDiagnosticRunView> detail(@PathVariable Long id) {
        return R.ok(viewMapper.toView(historyService.detail(id)));
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public R<List<ModelDiagnosticRunView>> sessionRuns(@PathVariable String sessionId) {
        List<AiModelDiagnosticRun> runs = historyService.sessionRuns(sessionId);
        return R.ok(runs.stream().map(viewMapper::toView).toList());
    }
}
