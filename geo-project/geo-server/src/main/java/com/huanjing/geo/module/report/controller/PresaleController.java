package com.huanjing.geo.module.report.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dispatch.service.DispatchFacadeService;
import com.huanjing.geo.module.report.dto.PresaleDiagnosisStartRequest;
import com.huanjing.geo.module.report.dto.PresaleQuestionSetGenerateRequest;
import com.huanjing.geo.module.report.dto.PresaleQuestionSetSaveRequest;
import com.huanjing.geo.module.report.entity.PresaleQuestionSet;
import com.huanjing.geo.module.report.service.PresaleQuestionSetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Presale")
@RestController
@RequestMapping("/api/presale")
@RequiredArgsConstructor
public class PresaleController {

    private final PresaleQuestionSetService presaleQuestionSetService;
    private final DispatchFacadeService dispatchFacadeService;

    @GetMapping("/question-sets")
    public R<List<PresaleQuestionSet>> list(@RequestParam Long projectId) {
        return R.ok(presaleQuestionSetService.listByProject(projectId));
    }

    @GetMapping("/question-sets/{setId}")
    public R<Map<String, Object>> detail(@PathVariable Long setId) {
        return R.ok(presaleQuestionSetService.detail(setId));
    }

    @PostMapping("/question-sets/generate")
    public R<Map<String, Object>> generate(@Valid @RequestBody PresaleQuestionSetGenerateRequest req) {
        return R.ok(presaleQuestionSetService.generate(req.getProjectId(), Boolean.TRUE.equals(req.getRegenerate())));
    }

    @PutMapping("/question-sets/{setId}")
    public R<Map<String, Object>> saveItems(@PathVariable Long setId, @Valid @RequestBody PresaleQuestionSetSaveRequest req) {
        return R.ok(presaleQuestionSetService.saveItems(setId, req.getItems()));
    }

    @PutMapping("/question-sets/{setId}/lock")
    public R<PresaleQuestionSet> lock(@PathVariable Long setId) {
        return R.ok(presaleQuestionSetService.lock(setId));
    }

    @PostMapping("/diagnosis/start")
    public R<?> startDiagnosis(@Valid @RequestBody PresaleDiagnosisStartRequest req) {
        return R.ok(dispatchFacadeService.enqueuePresaleDiagnosis(req.getProjectId(), req.getQuestionSetId(), req.getRemark()));
    }
}
