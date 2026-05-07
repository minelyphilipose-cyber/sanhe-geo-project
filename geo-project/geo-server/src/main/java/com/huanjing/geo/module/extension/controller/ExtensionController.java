package com.huanjing.geo.module.extension.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.extension.dto.BindCodeCreateRequest;
import com.huanjing.geo.module.extension.dto.BindCodeCreateResponse;
import com.huanjing.geo.module.extension.dto.ExtensionBindRequest;
import com.huanjing.geo.module.extension.dto.ExtensionBindResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshRequest;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTaskStateResponse;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckRequest;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckResponse;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.service.ExtensionBindCodeService;
import com.huanjing.geo.module.extension.service.ExtensionCredentialService;
import com.huanjing.geo.module.extension.service.ExtensionSessionService;
import com.huanjing.geo.module.extension.service.ExtensionTaskStateService;
import com.huanjing.geo.module.extension.service.ExtensionVersionService;
import com.huanjing.geo.module.extension.service.FillTokenService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Extension")
@RestController
@RequestMapping("/api/v1/extension")
@RequiredArgsConstructor
public class ExtensionController {

    private static final String EXTENSION_TOKEN_HEADER = "X-Ext-Token";

    private final ExtensionBindCodeService bindCodeService;
    private final ExtensionSessionService sessionService;
    private final ExtensionVersionService versionService;
    private final FillTokenService fillTokenService;
    private final ExtensionCredentialService credentialService;
    private final ExtensionTaskStateService taskStateService;
    private final CurrentUserService currentUserService;

    @PostMapping("/bind-codes")
    public R<BindCodeCreateResponse> createBindCode(@Valid @RequestBody BindCodeCreateRequest request) {
        SysUser current = currentUserService.requireCurrentUser();
        return R.ok(bindCodeService.create(request.brandId(), current.getId()));
    }

    @PostMapping("/bind")
    public R<ExtensionBindResponse> bind(@Valid @RequestBody ExtensionBindRequest request, HttpServletRequest servletRequest) {
        String platform = "chrome";
        versionService.requireSupported(platform, request.extensionVersion());
        ExtensionBindCodeService.BindCodePayload payload =
                bindCodeService.consume(request.bindCode(), request.brandId(), clientIp(servletRequest));
        return R.ok(sessionService.createBoundSession(
                payload.brandId(),
                payload.operatorId(),
                request.installId(),
                request.deviceFingerprint(),
                request.extensionVersion(),
                servletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/version-check")
    public R<ExtensionVersionCheckResponse> versionCheck(@Valid @RequestBody ExtensionVersionCheckRequest request) {
        return R.ok(versionService.checkOrThrow(request.platform(), request.currentVersion()));
    }

    @PostMapping("/token/refresh")
    public R<ExtensionTokenRefreshResponse> refresh(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @RequestBody(required = false) ExtensionTokenRefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        String version = request == null ? null : request.extensionVersion();
        return R.ok(sessionService.validateAndMaybeRenew(extensionToken, version, servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/token/{sessionId}/revoke")
    public R<Void> revokeToken(@PathVariable Long sessionId) {
        SysUser current = currentUserService.requireCurrentUser();
        sessionService.revoke(sessionId, current.getId());
        return R.ok();
    }

    @PostMapping("/fill-token/issue")
    public R<FillTokenIssueResponse> issueFillToken(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @Valid @RequestBody FillTokenIssueRequest request,
            HttpServletRequest servletRequest
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        String platform = StringUtils.hasText(request.platform()) ? request.platform() : "chrome";
        String version = StringUtils.hasText(request.extensionVersion())
                ? request.extensionVersion()
                : session.getExtensionVersion();
        return R.ok(fillTokenService.issue(
                request.accountId(),
                request.brandId(),
                session.getOperatorId(),
                request.taskTargetId(),
                platform,
                version
        ));
    }

    @PostMapping("/fill-token/consume")
    public R<ExtensionFillTokenConsumeResponse> consumeFillToken(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @Valid @RequestBody FillTokenConsumeRequest request,
            HttpServletRequest servletRequest
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        String platform = StringUtils.hasText(request.platform()) ? request.platform() : "chrome";
        String version = StringUtils.hasText(request.extensionVersion())
                ? request.extensionVersion()
                : session.getExtensionVersion();
        versionService.requireSupported(platform, version);
        return R.ok(credentialService.consumeFillTokenAndDecrypt(
                request.fillToken(),
                session.getOperatorId(),
                session.getId(),
                clientIp(servletRequest)
        ));
    }

    @PostMapping("/tasks/{taskId}/ack")
    public R<ExtensionTaskStateResponse> ackTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.ackFilled(taskId, session.getOperatorId(), session.getId()));
    }

    @PostMapping("/tasks/{taskId}/heartbeat")
    public R<ExtensionTaskStateResponse> heartbeatTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.heartbeat(
                taskId,
                session.getOperatorId(),
                session.getId()
        ));
    }

    @PostMapping("/tasks/{taskId}/published")
    public R<ExtensionTaskStateResponse> publishTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.published(taskId, session.getOperatorId(), session.getId()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
