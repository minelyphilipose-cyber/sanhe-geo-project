package com.huanjing.geo.module.extension.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentBrandLoginStatusRequest;
import com.huanjing.geo.module.content.dto.BrowserEnvironmentLoginStatusRequest;
import com.huanjing.geo.module.content.service.BrowserEnvironmentService;
import com.huanjing.geo.module.content.vo.BrowserEnvironmentAccountVO;
import com.huanjing.geo.module.extension.dto.BindCodeCreateRequest;
import com.huanjing.geo.module.extension.dto.BindCodeCreateResponse;
import com.huanjing.geo.module.extension.dto.ExtensionBindRequest;
import com.huanjing.geo.module.extension.dto.ExtensionBindResponse;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureRequest;
import com.huanjing.geo.module.extension.dto.ExtensionCookieCaptureResponse;
import com.huanjing.geo.module.extension.dto.ExtensionSelfMediaAccountResponse;
import com.huanjing.geo.module.extension.dto.ExtensionSessionVO;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshRequest;
import com.huanjing.geo.module.extension.dto.ExtensionTokenRefreshResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListItemResponse;
import com.huanjing.geo.module.extension.dto.ExtensionTaskPublishReportRequest;
import com.huanjing.geo.module.extension.dto.ExtensionTaskStateResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentExtensionSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckRequest;
import com.huanjing.geo.module.extension.dto.ExtensionVersionCheckResponse;
import com.huanjing.geo.module.extension.dto.ExtensionFillTokenConsumeResponse;
import com.huanjing.geo.module.extension.dto.FillTokenConsumeRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueRequest;
import com.huanjing.geo.module.extension.dto.FillTokenIssueResponse;
import com.huanjing.geo.module.extension.entity.ExtensionSession;
import com.huanjing.geo.module.extension.service.ExtensionBindCodeService;
import com.huanjing.geo.module.extension.service.ExtensionCredentialService;
import com.huanjing.geo.module.extension.service.ExtensionCookieCaptureService;
import com.huanjing.geo.module.extension.service.ExtensionFillTokenIssueService;
import com.huanjing.geo.module.extension.service.ExtensionSessionService;
import com.huanjing.geo.module.extension.service.ExtensionTaskListService;
import com.huanjing.geo.module.extension.service.ExtensionTaskStateService;
import com.huanjing.geo.module.extension.service.ExtensionVersionService;
import com.huanjing.geo.module.extension.service.LocalAgentSessionService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Extension")
@RestController
@RequestMapping("/api/v1/extension")
@RequiredArgsConstructor
public class ExtensionController {

    private static final String EXTENSION_TOKEN_HEADER = "X-Ext-Token";

    private final ExtensionBindCodeService bindCodeService;
    private final ExtensionSessionService sessionService;
    private final ExtensionVersionService versionService;
    private final ExtensionFillTokenIssueService fillTokenIssueService;
    private final ExtensionCredentialService credentialService;
    private final ExtensionCookieCaptureService cookieCaptureService;
    private final ExtensionTaskListService taskListService;
    private final ExtensionTaskStateService taskStateService;
    private final CurrentUserService currentUserService;
    private final BrowserEnvironmentService browserEnvironmentService;
    private final LocalAgentSessionService localAgentSessionService;

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

    @GetMapping("/brands/{brandId}/sessions")
    public R<List<ExtensionSessionVO>> listBrandSessions(@PathVariable Long brandId) {
        return R.ok(sessionService.listActiveByBrand(brandId));
    }

    @PostMapping("/brands/{brandId}/sessions/{sessionId}/revoke")
    public R<Void> revokeBrandSession(@PathVariable Long brandId, @PathVariable Long sessionId) {
        sessionService.revokeForBrand(brandId, sessionId);
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
        return R.ok(fillTokenIssueService.issue(
                request,
                session.getOperatorId(),
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
        return R.ok(credentialService.consumeFillToken(
                request.fillToken(),
                session.getOperatorId(),
                session.getId()
        ));
    }

    @GetMapping("/tasks")
    public R<List<ExtensionTaskListItemResponse>> listTasks(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskListService.listTasksForSessionOperator(session.getOperatorId()));
    }

    @GetMapping("/self-media-accounts")
    public R<List<ExtensionSelfMediaAccountResponse>> listSelfMediaAccounts(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(cookieCaptureService.listAccounts(session.getOperatorId()));
    }

    @PostMapping("/browser-environment-accounts/{id}/login-status")
    public R<BrowserEnvironmentAccountVO> reportBrowserEnvironmentLoginStatus(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long id,
            @Valid @RequestBody BrowserEnvironmentLoginStatusRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(browserEnvironmentService.reportLoginStatusForExtension(
                id,
                request,
                session.getOperatorId()
        ));
    }

    @PostMapping("/browser-environment-login-status")
    public R<BrowserEnvironmentAccountVO> reportBrowserEnvironmentLoginStatusByEnvironmentAndPlatform(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @Valid @RequestBody BrowserEnvironmentLoginStatusRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(browserEnvironmentService.reportLoginStatusForExtensionByEnvironmentAndPlatform(
                request,
                session.getOperatorId()
        ));
    }

    @PostMapping("/brands/{brandId}/browser-environment-login-status")
    public R<BrowserEnvironmentAccountVO> reportBrowserEnvironmentLoginStatusByBrandAndPlatform(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long brandId,
            @Valid @RequestBody BrowserEnvironmentBrandLoginStatusRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        if (!brandId.equals(session.getBrandId())) {
            return R.fail(403, "brandId does not match extension session");
        }
        return R.ok(browserEnvironmentService.reportLoginStatusForExtensionByBrandAndPlatform(
                brandId,
                request,
                session.getOperatorId()
        ));
    }

    @PostMapping("/local-agent/sign")
    public R<LocalAgentSignResponse> signLocalAgentRequest(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @Valid @RequestBody LocalAgentExtensionSignRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(localAgentSessionService.signRequestForExtension(session, request));
    }

    @PostMapping("/cookies/capture")
    public R<ExtensionCookieCaptureResponse> captureCookies(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @Valid @RequestBody ExtensionCookieCaptureRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported(request.platform(), request.extensionVersion());
        return R.ok(cookieCaptureService.capture(request, session.getOperatorId(), session.getId()));
    }

    @PostMapping("/tasks/{taskId}/ack")
    public R<ExtensionTaskStateResponse> ackTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.ackFilled(taskId, session.getOperatorId(), session.getId(), request));
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
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) ExtensionTaskPublishReportRequest request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.published(taskId, session.getOperatorId(), session.getId(), request));
    }

    @PostMapping("/tasks/{taskId}/abandon")
    public R<ExtensionTaskStateResponse> abandonTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.abandon(taskId, session.getOperatorId(), session.getId()));
    }

    @PostMapping("/tasks/{taskId}/fail")
    public R<ExtensionTaskStateResponse> failTask(
            @RequestHeader(EXTENSION_TOKEN_HEADER) String extensionToken,
            @PathVariable Long taskId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        ExtensionSession session = sessionService.requireActiveSession(extensionToken);
        versionService.requireSupported("chrome", session.getExtensionVersion());
        return R.ok(taskStateService.fail(taskId, session.getOperatorId(), session.getId(), request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
