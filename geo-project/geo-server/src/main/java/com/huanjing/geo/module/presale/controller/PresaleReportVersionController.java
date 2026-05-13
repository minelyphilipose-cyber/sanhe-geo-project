package com.huanjing.geo.module.presale.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.DeriveVersionRequest;
import com.huanjing.geo.module.presale.dto.DeriveVersionResponse;
import com.huanjing.geo.module.presale.dto.EditVersionContentRequest;
import com.huanjing.geo.module.presale.dto.FreezeVersionRequest;
import com.huanjing.geo.module.presale.dto.RetryVersionResponse;
import com.huanjing.geo.module.presale.dto.VersionActionResponse;
import com.huanjing.geo.module.presale.service.PresaleReportVersionActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售前报告版本"写动作"接口集合。
 *
 * <p>只承载对某个已有 version 做状态变更的接口(edit/derive/freeze/unfreeze/delete/retry),
 * 读模型(列表、详情、最新版、进度)仍在 {@code PresaleReportController} 中。</p>
 *
 * <p>路径前缀与 {@code PresaleReportController} 共用 {@code /api/presale/reports}。
 * 两个 Controller 共存不冲突,因为各自 @*Mapping 的子路径互斥。</p>
 *
 * <p>权限(由 Service 层 {@code ensurePermission} 统一处理):
 * <ul>
 *   <li>{@code presale.report.edit_content} —— edit/derive/freeze/retry(沿用 V62 已有 seed)</li>
 *   <li>{@code presale.report.manage} —— unfreeze/delete(V65 新增 seed)</li>
 * </ul>
 * </p>
 */
@Tag(name = "Presale Report - Version Actions")
@RestController
@RequestMapping("/api/presale/reports")
@RequiredArgsConstructor
public class PresaleReportVersionController {

    private final PresaleReportVersionActionService actionService;

    @Operation(summary = "Edit version L3 content")
    @PatchMapping("/{id}/versions/{versionNo}/content")
    public R<VersionActionResponse> editContent(
            @PathVariable Long id,
            @PathVariable Integer versionNo,
            @Valid @RequestBody EditVersionContentRequest req
    ) {
        return R.ok(actionService.editContent(id, versionNo, req));
    }

    @Operation(summary = "Derive a new version")
    @PostMapping("/{id}/versions/{versionNo}/derive")
    public R<DeriveVersionResponse> derive(
            @PathVariable Long id,
            @PathVariable Integer versionNo,
            @RequestBody(required = false) DeriveVersionRequest req
    ) {
        return R.ok(actionService.derive(id, versionNo,
                req == null ? new DeriveVersionRequest() : req));
    }

    @Operation(summary = "Freeze version")
    @PostMapping("/{id}/versions/{versionNo}/freeze")
    public R<VersionActionResponse> freeze(
            @PathVariable Long id,
            @PathVariable Integer versionNo,
            @Valid @RequestBody(required = false) FreezeVersionRequest req
    ) {
        return R.ok(actionService.freeze(id, versionNo, req));
    }

    @Operation(summary = "Unfreeze version (manager only)")
    @PostMapping("/{id}/versions/{versionNo}/unfreeze")
    public R<VersionActionResponse> unfreeze(
            @PathVariable Long id,
            @PathVariable Integer versionNo
    ) {
        return R.ok(actionService.unfreeze(id, versionNo));
    }

    @Operation(summary = "Delete version (manager + not exported)")
    @DeleteMapping("/{id}/versions/{versionNo}")
    public R<Void> delete(
            @PathVariable Long id,
            @PathVariable Integer versionNo
    ) {
        actionService.delete(id, versionNo);
        return R.ok();
    }

    @Operation(summary = "Retry a FAILED generation")
    @PostMapping("/{id}/versions/{versionNo}/retry")
    public R<RetryVersionResponse> retry(
            @PathVariable Long id,
            @PathVariable Integer versionNo
    ) {
        return R.ok(actionService.retry(id, versionNo));
    }

    @Operation(summary = "Cancel a QUEUED/RUNNING generation")
    @PostMapping("/{id}/versions/{versionNo}/cancel-generation")
    public R<RetryVersionResponse> cancelGeneration(
            @PathVariable Long id,
            @PathVariable Integer versionNo
    ) {
        return R.ok(actionService.cancelGeneration(id, versionNo));
    }

    @Operation(summary = "Regenerate a DONE/FAILED version in place")
    @PostMapping("/{id}/versions/{versionNo}/regenerate")
    public R<RetryVersionResponse> regenerate(
            @PathVariable Long id,
            @PathVariable Integer versionNo
    ) {
        return R.ok(actionService.regenerate(id, versionNo));
    }
}
