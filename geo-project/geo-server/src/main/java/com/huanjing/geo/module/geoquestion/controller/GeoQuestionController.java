package com.huanjing.geo.module.geoquestion.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.geoquestion.dto.GeoQuestionDtos.*;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionVersion;
import com.huanjing.geo.module.geoquestion.service.GeoQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class GeoQuestionController {
    private final GeoQuestionService geoQuestionService;

    @GetMapping("/api/geo/customers/search")
    public R<List<CustomerSearchItem>> searchCustomers(@RequestParam(required = false) String keyword) {
        return R.ok(geoQuestionService.searchCustomers(keyword));
    }

    @GetMapping("/api/geo/customers/{id:\\d+}/profile")
    public R<ProfileVO> profile(@PathVariable Long id) {
        return R.ok(geoQuestionService.profile(id));
    }

    @GetMapping("/api/geo/projects/{id:\\d+}/profile")
    public R<ProfileVO> projectProfile(@PathVariable Long id) {
        return R.ok(geoQuestionService.profileByProject(id));
    }

    @GetMapping("/api/geo/customers/{id:\\d+}/quota")
    public R<QuotaSnapshot> quota(@PathVariable Long id, @RequestParam(required = false) Long workorderId) {
        return R.ok(geoQuestionService.quotaSnapshot(id, workorderId));
    }

    @GetMapping("/api/geo/projects/{id:\\d+}/quota")
    public R<QuotaSnapshot> projectQuota(@PathVariable Long id, @RequestParam(required = false) Long workorderId) {
        return R.ok(geoQuestionService.quotaSnapshotByProject(id, workorderId));
    }

    @GetMapping("/api/geo/customers/{id:\\d+}/workorders")
    public R<List<WorkorderListItemVO>> workorders(@PathVariable Long id) {
        return R.ok(geoQuestionService.workorders(id));
    }

    @GetMapping("/api/geo/projects/{id:\\d+}/workorders")
    public R<List<WorkorderListItemVO>> projectWorkorders(@PathVariable Long id) {
        return R.ok(geoQuestionService.workordersByProject(id));
    }

    @GetMapping("/api/llm/providers")
    public R<List<ProviderVO>> providers() {
        return R.ok(geoQuestionService.providers());
    }

    @PostMapping("/api/geo/workorder/create-or-get")
    public R<WorkorderVO> createOrGet(@RequestBody CreateOrGetWorkorderRequest req) {
        if (req.getProjectId() != null) {
            return R.ok(geoQuestionService.createOrGetByProject(req.getProjectId()));
        }
        return R.ok(geoQuestionService.createOrGet(req.getCompanyId()));
    }

    @GetMapping("/api/geo/workorder/{id:\\d+}/review")
    public R<ReviewVO> review(@PathVariable Long id) {
        return R.ok(geoQuestionService.review(id));
    }

    @GetMapping("/api/geo/workorder/{id:\\d+}/questions")
    public R<QuestionPageVO> questions(@PathVariable Long id,
                                       @RequestParam(required = false, defaultValue = "all") String tier,
                                       @RequestParam(required = false, defaultValue = "1") Long current,
                                       @RequestParam(required = false, defaultValue = "20") Long size) {
        return R.ok(geoQuestionService.questionPage(id, tier, current == null ? 1L : current, size == null ? 20L : size));
    }

    @PostMapping("/api/geo/workorder/{id:\\d+}/questions/manual")
    public R<ReviewVO> createManualQuestions(@PathVariable Long id, @RequestBody ManualQuestionCreateRequest req) {
        return R.ok(geoQuestionService.createManualQuestions(id, req));
    }

    @PostMapping("/api/geo/workorder/{id:\\d+}/commit")
    public R<GeoQuestionVersion> commit(@PathVariable Long id, @RequestBody CommitRequest req) {
        return R.ok(geoQuestionService.commit(id, req));
    }

    @PostMapping("/api/geo/workorder/{id:\\d+}/partner-review/submit")
    public R<WorkorderVO> submitPartnerReview(@PathVariable Long id) {
        return R.ok(geoQuestionService.submitPartnerReview(id));
    }

    @PostMapping("/api/geo/workorder/{id:\\d+}/partner-review/return")
    public R<WorkorderVO> returnPartnerReview(@PathVariable Long id, @RequestBody PartnerReviewReturnRequest req) {
        return R.ok(geoQuestionService.returnPartnerReview(id, req));
    }

    @PostMapping("/api/geo/workorder/{id:\\d+}/partner-review/submit-hq")
    public R<WorkorderVO> submitPartnerReviewToHq(@PathVariable Long id) {
        return R.ok(geoQuestionService.submitPartnerReviewToHq(id));
    }

    @GetMapping("/api/geo/workorder/{id:\\d+}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        String filename = java.net.URLEncoder.encode("问题池工单-" + id + ".csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(geoQuestionService.exportCsv(id));
    }

    @GetMapping("/api/geo/draft/{workorderId:\\d+}")
    public R<DraftVO> draft(@PathVariable Long workorderId) {
        return R.ok(geoQuestionService.getDraft(workorderId));
    }

    @PostMapping("/api/geo/draft/save")
    public R<DraftVO> saveDraft(@RequestBody DraftSaveRequest req) {
        return R.ok(geoQuestionService.saveDraft(req));
    }

    @PostMapping("/api/geo/batch/start")
    public R<BatchVO> startBatch(@RequestBody BatchStartRequest req) {
        return R.ok(geoQuestionService.startBatch(req));
    }

    @PostMapping("/api/geo/partner/workorder/{id:\\d+}/core-questions/generate")
    public R<BatchVO> generatePartnerCoreQuestions(@PathVariable Long id,
                                                   @RequestBody(required = false) PartnerCoreQuestionGenerateRequest req) {
        return R.ok(geoQuestionService.startPartnerCoreQuestionBatch(id, req));
    }

    @GetMapping("/api/geo/batch/{id:\\d+}")
    public R<BatchVO> batch(@PathVariable Long id) {
        return R.ok(geoQuestionService.batch(id));
    }

    @PostMapping("/api/geo/batch/{id:\\d+}/cancel")
    public R<Void> cancelBatch(@PathVariable Long id) {
        geoQuestionService.cancelBatch(id);
        return R.ok();
    }

    @DeleteMapping("/api/geo/batch/{id:\\d+}")
    public R<Void> deleteBatch(@PathVariable Long id) {
        geoQuestionService.deleteBatch(id);
        return R.ok();
    }

    @PostMapping("/api/geo/question/{id:\\d+}/regenerate")
    public R<RegenerateQuestionVO> regenerateQuestion(@PathVariable Long id, @RequestBody(required = false) RegenerateQuestionRequest req) {
        return R.ok(geoQuestionService.regenerateQuestion(id, req));
    }

    @PutMapping("/api/geo/question/{id:\\d+}")
    public R<QuestionVO> updateQuestion(@PathVariable Long id, @RequestBody QuestionUpdateRequest req) {
        return R.ok(geoQuestionService.updateQuestion(id, req));
    }

    @PostMapping("/api/geo/questions/batch-delete")
    public R<Integer> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest req) {
        return R.ok(geoQuestionService.deleteQuestions(req));
    }

    @DeleteMapping("/api/geo/question/{id:\\d+}")
    public R<Void> deleteQuestion(@PathVariable Long id) {
        geoQuestionService.deleteQuestion(id);
        return R.ok();
    }
}
