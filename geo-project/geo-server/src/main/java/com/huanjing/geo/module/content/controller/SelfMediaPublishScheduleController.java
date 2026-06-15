package com.huanjing.geo.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.SelfMediaPlatformQuickScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCancelRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleManualResultRequest;
import com.huanjing.geo.module.content.service.SelfMediaPublishAutoScheduleService;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.content.vo.SelfMediaAutomationOverviewVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPlatformQuickScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SelfMediaPublishSchedule")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class SelfMediaPublishScheduleController {
    private final SelfMediaPublishScheduleService scheduleService;
    private final SelfMediaPublishAutoScheduleService autoScheduleService;

    @PostMapping("/self-media-schedules")
    public R<SelfMediaPublishScheduleCreateResponse> createSchedules(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SelfMediaPublishScheduleCreateRequest request) {
        return R.ok(scheduleService.createSchedules(request, idempotencyKey));
    }

    @PostMapping("/self-media-schedules/platform-quick-preview")
    public R<SelfMediaPlatformQuickScheduleResponse> previewPlatformQuickSchedule(
            @Valid @RequestBody SelfMediaPlatformQuickScheduleRequest request) {
        return R.ok(scheduleService.previewPlatformQuickSchedule(request));
    }

    @PostMapping("/self-media-schedules/platform-quick-create")
    public R<SelfMediaPlatformQuickScheduleResponse> createPlatformQuickSchedule(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SelfMediaPlatformQuickScheduleRequest request) {
        return R.ok(scheduleService.createPlatformQuickSchedule(request, idempotencyKey));
    }

    @PostMapping("/self-media-schedules/platform-quick-dispatch")
    public R<SelfMediaPlatformQuickScheduleResponse> dispatchPlatformQuickSchedule(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SelfMediaPlatformQuickScheduleRequest request) {
        return R.ok(scheduleService.dispatchPlatformQuickSchedule(request, idempotencyKey));
    }

    @PostMapping("/self-media-schedules/auto-preview")
    public R<SelfMediaPublishAutoScheduleResponse> previewAutoSchedules(
            @Valid @RequestBody SelfMediaPublishAutoScheduleRequest request) {
        return R.ok(autoScheduleService.preview(request));
    }

    @PostMapping("/self-media-schedules/auto-create")
    public R<SelfMediaPublishAutoScheduleResponse> createAutoSchedules(
            @Valid @RequestBody SelfMediaPublishAutoScheduleRequest request) {
        return R.ok(autoScheduleService.create(request));
    }

    @GetMapping("/self-media-schedules")
    public R<Page<SelfMediaPublishScheduleVO>> pageSchedules(@RequestParam(required = false) Long brandId,
                                                             @RequestParam(required = false) String platform,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String failureCode,
                                                             @RequestParam(required = false) Long articleId,
                                                             @RequestParam(required = false) Long selfMediaAccountId,
                                                             @RequestParam(defaultValue = "1") Long current,
                                                             @RequestParam(defaultValue = "20") Long size) {
        return R.ok(scheduleService.pageSchedules(brandId, platform, status, failureCode, articleId, selfMediaAccountId, current, size));
    }

    @GetMapping("/self-media-automation/overview")
    public R<SelfMediaAutomationOverviewVO> automationOverview() {
        return R.ok(scheduleService.automationOverview());
    }

    @GetMapping("/self-media-schedules/{id}")
    public R<SelfMediaPublishScheduleVO> detail(@PathVariable Long id) {
        return R.ok(scheduleService.detail(id));
    }

    @PostMapping("/self-media-schedules/{id}/cancel")
    public R<SelfMediaPublishScheduleVO> cancel(@PathVariable Long id,
                                                @RequestBody(required = false) SelfMediaPublishScheduleCancelRequest request) {
        return R.ok(scheduleService.cancel(id, request == null ? null : request.getReason()));
    }

    @PostMapping("/self-media-schedules/{id}/confirm-platform-cancelled")
    public R<SelfMediaPublishScheduleVO> confirmPlatformCancelled(@PathVariable Long id,
                                                                  @RequestBody(required = false) SelfMediaPublishScheduleCancelRequest request) {
        return R.ok(scheduleService.confirmPlatformCancelled(id, request == null ? null : request.getReason()));
    }

    @PostMapping("/self-media-schedules/{id}/confirm-published")
    public R<SelfMediaPublishScheduleVO> confirmPublished(@PathVariable Long id,
                                                          @RequestBody(required = false) SelfMediaPublishScheduleManualResultRequest request) {
        return R.ok(scheduleService.confirmPublished(id, request == null ? null : request.getPlatformPublishedUrl()));
    }

    @PostMapping("/self-media-schedules/{id}/confirm-publish-failed")
    public R<SelfMediaPublishScheduleVO> confirmPublishFailed(@PathVariable Long id,
                                                              @RequestBody(required = false) SelfMediaPublishScheduleManualResultRequest request) {
        String failureCode = request == null ? null : request.getFailureCode();
        String failureMessage = request == null ? null : request.getFailureMessage();
        return R.ok(scheduleService.confirmPublishFailed(id, failureCode, failureMessage));
    }

    @PostMapping("/self-media-schedules/{id}/retry-now")
    public R<SelfMediaPublishScheduleVO> retryNow(@PathVariable Long id) {
        return R.ok(scheduleService.retryNow(id));
    }

    @PostMapping("/self-media-schedules/{id}/mark-manual-required")
    public R<SelfMediaPublishScheduleVO> markManualRequired(@PathVariable Long id,
                                                            @RequestBody(required = false) SelfMediaPublishScheduleCancelRequest request) {
        return R.ok(scheduleService.markManualRequired(id, request == null ? null : request.getReason()));
    }

    @PostMapping("/self-media-schedules/{id}/recheck-publish-result")
    public R<SelfMediaPublishScheduleVO> recheckPublishResult(@PathVariable Long id) {
        return R.ok(scheduleService.recheckPublishResult(id));
    }
}
