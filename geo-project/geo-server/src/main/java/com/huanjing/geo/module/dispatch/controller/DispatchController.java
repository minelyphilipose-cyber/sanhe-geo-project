package com.huanjing.geo.module.dispatch.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.dispatch.dto.DispatchTaskReplayRequest;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillRequest;
import com.huanjing.geo.module.dispatch.dto.PollSummaryBackfillResponse;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.service.DispatchFacadeService;
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
}
