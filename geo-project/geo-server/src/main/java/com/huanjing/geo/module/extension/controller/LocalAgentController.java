package com.huanjing.geo.module.extension.controller;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSelfMediaScheduleClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSessionVO;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.service.LocalAgentSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Tag(name = "LocalAgent")
@RestController
@RequestMapping("/api/v1/local-agent")
@RequiredArgsConstructor
public class LocalAgentController {
    private final LocalAgentSessionService service;
    private final SelfMediaPublishScheduleService scheduleService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;

    @PostMapping("/pairing-intents")
    public R<LocalAgentPairingIntentResponse> registerPairingIntent(
            @Valid @RequestBody LocalAgentPairingIntentRequest request) {
        return R.ok(service.registerPairingIntent(request));
    }

    @PostMapping("/pairings/approve")
    public R<LocalAgentPairingApproveResponse> approvePairing(
            @Valid @RequestBody LocalAgentPairingApproveRequest request) {
        return R.ok(service.approvePairing(request));
    }

    @PostMapping("/pairings/claim")
    public R<LocalAgentPairingClaimResponse> claimPairing(
            @Valid @RequestBody LocalAgentPairingClaimRequest request,
            HttpServletRequest servletRequest) {
        return R.ok(service.claimPairing(request, servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/sessions")
    public R<List<LocalAgentSessionVO>> listSessions() {
        return R.ok(service.listActiveSessions());
    }

    @PostMapping("/sessions/{sessionId}/sign")
    public R<LocalAgentSignResponse> signRequest(@PathVariable Long sessionId,
                                                 @Valid @RequestBody LocalAgentSignRequest request) {
        return R.ok(service.signRequest(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public R<Void> revoke(@PathVariable Long sessionId) {
        service.revoke(sessionId);
        return R.ok();
    }

    @GetMapping("/self-media-schedules/claim-next")
    public R<LocalAgentSelfMediaScheduleClaimResponse> claimNextSelfMediaSchedule(
            @RequestParam(required = false) String platform,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        SelfMediaPublishScheduleService.ClaimedScheduleTask claimed =
                scheduleService.claimNextTaskForLocalAgent(session.getOperatorId(), platform, 30);
        if (claimed == null) {
            return R.ok(null);
        }
        DistributionTask task = claimed.task();
        SelfMediaAccount account = task.getSelfMediaAccountId() == null
                ? null
                : selfMediaAccountMapper.selectById(task.getSelfMediaAccountId());
        String taskPlatform = StringUtils.hasText(claimed.schedule().getPlatform())
                ? claimed.schedule().getPlatform()
                : account == null ? null : account.getPlatform();
        LocalAgentSelfMediaScheduleClaimResponse.Launch launch =
                new LocalAgentSelfMediaScheduleClaimResponse.Launch(
                        task.getId(),
                        taskPlatform,
                        defaultPublishUrl(taskPlatform),
                        task.getSelfMediaAccountId(),
                        task.getBrowserEnvironmentAccountId(),
                        account == null ? null : account.getPlatformAccountId(),
                        account == null ? null : account.getAccountName(),
                        task.getEnvironmentKey(),
                        task.getEnvironmentKey(),
                        task.getProviderProfileId()
                );
        return R.ok(new LocalAgentSelfMediaScheduleClaimResponse(claimed.schedule(), task, launch));
    }

    private LocalAgentSession verifySignedRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (StringUtils.hasText(request.getQueryString())) {
            path += "?" + request.getQueryString();
        }
        return service.verifySignedRequest(
                request.getMethod(),
                path,
                sha256Hex(""),
                request.getHeader("X-Geo-Helper-Access"),
                request.getHeader("X-Geo-Helper-Timestamp"),
                request.getHeader("X-Geo-Helper-Nonce"),
                request.getHeader("X-Geo-Helper-Signature"),
                request.getHeader("User-Agent")
        );
    }

    private String defaultPublishUrl(String platform) {
        if ("toutiao".equalsIgnoreCase(platform)) {
            return "https://mp.toutiao.com/profile_v4/graphic/publish";
        }
        if ("zhihu".equalsIgnoreCase(platform)) {
            return "https://www.zhihu.com/";
        }
        if ("xiaohongshu".equalsIgnoreCase(platform)) {
            return "https://www.xiaohongshu.com/";
        }
        return null;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
