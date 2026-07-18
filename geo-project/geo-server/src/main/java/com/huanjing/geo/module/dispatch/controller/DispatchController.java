package com.huanjing.geo.module.dispatch.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskReplayRequest;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollBatchView;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollPlatformOption;
import com.huanjing.geo.module.dispatch.dto.ManualQuestionPollRequest;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillRequest;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillResponse;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.service.DispatchFacadeService;
import com.huanjing.geo.module.dispatch.service.ManualQuestionPollService;
import com.huanjing.geo.module.dispatch.service.PollSummaryBackfillService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Dispatch")
@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchFacadeService dispatchFacadeService;
    private final PollSummaryBackfillService pollSummaryBackfillService;
    private final ManualQuestionPollService manualQuestionPollService;

    @PostMapping("/tasks/replay")
    public R<Void> replayTask(@Valid @RequestBody DispatchTaskReplayRequest req) {
        dispatchFacadeService.replayTask(req.getTaskId());
        return R.ok();
    }

    @GetMapping("/tasks/replayable")
    public R<List<DispatchTask>> listReplayable(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return R.ok(dispatchFacadeService.listReplayableTasks(projectId, size));
    }

    @GetMapping("/tasks/{taskId}")
    public R<DispatchTask> getTaskStatus(@PathVariable Long taskId) {
        return R.ok(dispatchFacadeService.getTaskStatus(taskId));
    }

    @PostMapping("/poll-summary/backfill")
    public R<PollSummaryBackfillResponse> backfillPollSummary(@Valid @RequestBody PollSummaryBackfillRequest req) {
        return R.ok(pollSummaryBackfillService.backfill(req));
    }

    @GetMapping("/question-poll/manual/platforms")
    public R<List<ManualQuestionPollPlatformOption>> manualQuestionPollPlatforms() {
        return R.ok(manualQuestionPollService.platformOptions());
    }

    @PostMapping("/question-poll/manual")
    public R<ManualQuestionPollBatchView> startManualQuestionPoll(
            @Valid @RequestBody ManualQuestionPollRequest request
    ) {
        return R.ok(manualQuestionPollService.start(request));
    }

    @GetMapping("/question-poll/manual")
    public R<List<ManualQuestionPollBatchView>> listManualQuestionPolls(
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return R.ok(manualQuestionPollService.listRecent(size));
    }

    @GetMapping("/question-poll/manual/{batchId}")
    public R<ManualQuestionPollBatchView> getManualQuestionPoll(@PathVariable Long batchId) {
        return R.ok(manualQuestionPollService.get(batchId));
    }
}
