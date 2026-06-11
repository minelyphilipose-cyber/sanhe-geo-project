package com.huanjing.geo.module.extension.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.content.entity.BrowserEnvironment;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.config.SemiAutoPlatformProperties;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentAccountMapper;
import com.huanjing.geo.module.content.mapper.BrowserEnvironmentMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingApproveResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentPairingIntentResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSelfMediaPublishCheckClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSelfMediaScheduleClaimResponse;
import com.huanjing.geo.module.extension.dto.LocalAgentSelfMediaSchedulePlatformsResponse;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Tag(name = "LocalAgent")
@RestController
@RequestMapping("/api/v1/local-agent")
@RequiredArgsConstructor
public class LocalAgentController {
    private static final int SELF_MEDIA_SCHEDULE_LOCK_MINUTES = 3;
    private static final int SELF_MEDIA_PUBLISH_CHECK_LOCK_MINUTES = 3;

    private final LocalAgentSessionService service;
    private final SelfMediaPublishScheduleService scheduleService;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final BrowserEnvironmentMapper browserEnvironmentMapper;
    private final BrowserEnvironmentAccountMapper browserEnvironmentAccountMapper;
    private final SemiAutoPlatformProperties semiAutoPlatformProperties;

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

    @GetMapping("/self-media-schedules/platforms")
    public R<LocalAgentSelfMediaSchedulePlatformsResponse> selfMediaSchedulePlatforms(HttpServletRequest request) {
        verifySignedRequest(request);
        return R.ok(new LocalAgentSelfMediaSchedulePlatformsResponse(scheduleService.localAgentAutomationPlatforms()));
    }

    @GetMapping("/self-media-schedules/claim-next")
    public R<LocalAgentSelfMediaScheduleClaimResponse> claimNextSelfMediaSchedule(
            @RequestParam(required = false) String platform,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        SelfMediaPublishScheduleService.ClaimedScheduleTask claimed =
                scheduleService.claimNextTaskForLocalAgent(
                        session.getOperatorId(),
                        platform,
                        SELF_MEDIA_SCHEDULE_LOCK_MINUTES
                );
        if (claimed == null) {
            return R.ok(new LocalAgentSelfMediaScheduleClaimResponse(
                    null,
                    null,
                    null,
                    claimBlockedReason(platform)
            ));
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
        return R.ok(new LocalAgentSelfMediaScheduleClaimResponse(claimed.schedule(), task, launch, null));
    }

    @GetMapping("/self-media-schedules/publish-checks/claim-next")
    public R<LocalAgentSelfMediaPublishCheckClaimResponse> claimNextSelfMediaPublishCheck(
            @RequestParam(required = false) String platform,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        SelfMediaPublishScheduleVO schedule =
                scheduleService.claimNextPublishCheckForLocalAgent(
                        session.getOperatorId(),
                        platform,
                        SELF_MEDIA_PUBLISH_CHECK_LOCK_MINUTES
                );
        if (schedule == null) {
            return R.ok(new LocalAgentSelfMediaPublishCheckClaimResponse(
                    null,
                    null,
                    claimBlockedReason(platform)
            ));
        }
        SelfMediaAccount account = schedule.getSelfMediaAccountId() == null
                ? null
                : selfMediaAccountMapper.selectById(schedule.getSelfMediaAccountId());
        BrowserEnvironment environment = schedule.getBrowserEnvironmentId() == null
                ? null
                : browserEnvironmentMapper.selectById(schedule.getBrowserEnvironmentId());
        BrowserEnvironmentAccount environmentAccount = schedule.getBrowserEnvironmentAccountId() == null
                ? null
                : browserEnvironmentAccountMapper.selectById(schedule.getBrowserEnvironmentAccountId());
        String taskPlatform = StringUtils.hasText(schedule.getPlatform())
                ? schedule.getPlatform()
                : account == null ? null : account.getPlatform();
        LocalAgentSelfMediaPublishCheckClaimResponse.Launch launch =
                new LocalAgentSelfMediaPublishCheckClaimResponse.Launch(
                        schedule.getId(),
                        taskPlatform,
                        defaultWorksListUrl(taskPlatform, account),
                        schedule.getSelfMediaAccountId(),
                        schedule.getBrowserEnvironmentAccountId(),
                        firstText(
                                environmentAccount == null ? null : environmentAccount.getExpectedPlatformAccountId(),
                                account == null ? null : account.getPlatformAccountId()
                        ),
                        firstText(
                                environmentAccount == null ? null : environmentAccount.getExpectedAccountName(),
                                account == null ? null : account.getAccountName()
                        ),
                        environment == null ? null : environment.getEnvironmentKey(),
                        environment == null ? null : environment.getName(),
                        environment == null ? null : environment.getProviderProfileId()
                );
        return R.ok(new LocalAgentSelfMediaPublishCheckClaimResponse(schedule, launch, null));
    }

    private String claimBlockedReason(String platform) {
        Set<String> enabledPlatforms = Set.copyOf(scheduleService.localAgentAutomationPlatforms());
        String normalizedPlatform = normalizePlatform(platform);
        if (enabledPlatforms.isEmpty()) {
            return "PLATFORM_CAPABILITY_DISABLED";
        }
        if (StringUtils.hasText(normalizedPlatform) && !enabledPlatforms.contains(normalizedPlatform)) {
            return "PLATFORM_CAPABILITY_DISABLED";
        }
        String accountPlatform = StringUtils.hasText(normalizedPlatform) && enabledPlatforms.contains(normalizedPlatform)
                ? normalizedPlatform
                : null;
        if (!hasAvailableSelfMediaAccount(accountPlatform, enabledPlatforms)) {
            return "NO_AVAILABLE_ACCOUNT";
        }
        return "NO_DUE_TASK";
    }

    private boolean hasAvailableSelfMediaAccount(String platform, Set<String> enabledPlatforms) {
        LambdaQueryWrapper<SelfMediaAccount> query = new LambdaQueryWrapper<SelfMediaAccount>()
                .eq(SelfMediaAccount::getStatus, "active")
                .isNull(SelfMediaAccount::getDeletedAt)
                .last("LIMIT 1");
        if (StringUtils.hasText(platform)) {
            query.eq(SelfMediaAccount::getPlatform, platform);
        } else if (enabledPlatforms != null && !enabledPlatforms.isEmpty()) {
            query.in(SelfMediaAccount::getPlatform, enabledPlatforms);
        }
        return selfMediaAccountMapper.selectOne(query) != null;
    }

    private String normalizePlatform(String platform) {
        return StringUtils.hasText(platform) ? platform.trim().toLowerCase(Locale.ROOT) : null;
    }

    @PostMapping("/self-media-schedules/{scheduleId}/publish-checks/published")
    public R<SelfMediaPublishScheduleVO> markSelfMediaPublishCheckPublished(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String platformPublishedUrl,
            @RequestParam(required = false) String diagnosticsJson,
            HttpServletRequest request) {
        verifySignedRequest(request);
        return R.ok(scheduleService.markClaimedPublishedConfirmed(scheduleId, platformPublishedUrl, diagnosticsJson));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/publish-checks/unknown")
    public R<SelfMediaPublishScheduleVO> markSelfMediaPublishCheckUnknown(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String diagnosticsJson,
            HttpServletRequest request) {
        verifySignedRequest(request);
        return R.ok(scheduleService.markClaimedPublishCheckUnknown(scheduleId, diagnosticsJson));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/publish-checks/failed")
    public R<SelfMediaPublishScheduleVO> markSelfMediaPublishCheckFailed(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String failureMessage,
            @RequestParam(required = false) String diagnosticsJson,
            HttpServletRequest request) {
        verifySignedRequest(request);
        return R.ok(scheduleService.markClaimedPublishFailed(
                scheduleId,
                failureCode,
                failureMessage,
                diagnosticsJson
        ));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/executions/failed")
    public R<SelfMediaPublishScheduleVO> markSelfMediaScheduleExecutionFailed(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String failureMessage,
            @RequestParam(required = false) String diagnosticsJson,
            HttpServletRequest request) {
        verifySignedRequest(request);
        return R.ok(scheduleService.markLocalAgentExecutionFailed(
                scheduleId,
                failureCode,
                failureMessage,
                diagnosticsJson
        ));
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
        if ("baijiahao".equalsIgnoreCase(platform)) {
            return "https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1";
        }
        if (StringUtils.hasText(platform)) {
            try {
                return semiAutoPlatformProperties.profile(platform.toLowerCase()).publishUrl();
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
        return null;
    }

    private String defaultWorksListUrl(String platform) {
        if ("toutiao".equalsIgnoreCase(platform)) {
            return "https://mp.toutiao.com/profile_v4/manage/content/all";
        }
        if ("xiaohongshu".equalsIgnoreCase(platform)) {
            return "https://creator.xiaohongshu.com/new/note-manager";
        }
        if ("baijiahao".equalsIgnoreCase(platform)) {
            return null;
        }
        return defaultPublishUrl(platform);
    }

    private String defaultWorksListUrl(String platform, SelfMediaAccount account) {
        if (!"baijiahao".equalsIgnoreCase(platform)) {
            return defaultWorksListUrl(platform);
        }
        String appId = account == null ? null : account.getPlatformAccountId();
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        return "https://baijiahao.baidu.com/builder/rc/content"
                + "?currentPage=1&pageSize=10&search=&type=&collection=&app_id="
                + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8)
                + "&startDate=&endDate=";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
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
