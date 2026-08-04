package com.huanjing.geo.module.extension.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusReportRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentRuntimeStatusVO;
import com.huanjing.geo.module.extension.dto.LocalAgentSessionVO;
import com.huanjing.geo.module.extension.dto.LocalAgentSignRequest;
import com.huanjing.geo.module.extension.dto.LocalAgentSignResponse;
import com.huanjing.geo.module.extension.entity.LocalAgentSession;
import com.huanjing.geo.module.extension.service.LocalAgentSessionService;
import com.huanjing.geo.module.extension.service.SelfMediaRuntimeStatusService;
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
import java.util.Map;
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
    private final ObjectMapper objectMapper;
    private final SelfMediaRuntimeStatusService runtimeStatusService;

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

    @PostMapping("/runtime-status")
    public R<LocalAgentRuntimeStatusVO> reportRuntimeStatus(
            @RequestBody(required = false) LocalAgentRuntimeStatusReportRequest body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        return R.ok(runtimeStatusService.reportLocalAgent(session, body));
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
                        session.getId(),
                        platform,
                        SELF_MEDIA_SCHEDULE_LOCK_MINUTES
                );
        if (claimed == null) {
            SelfMediaPublishScheduleService.LocalAgentClaimBlock block =
                    scheduleService.consumeLastLocalAgentClaimBlock();
            return R.ok(new LocalAgentSelfMediaScheduleClaimResponse(
                    null,
                    null,
                    null,
                    block == null ? claimBlockedReason(platform) : block.reason(),
                    block == null ? null : block.retryAfterSeconds()
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
                        null,
                        account == null ? null : account.getAccountName(),
                        task.getEnvironmentKey(),
                        task.getEnvironmentKey(),
                        task.getProviderProfileId()
                );
        return R.ok(new LocalAgentSelfMediaScheduleClaimResponse(claimed.schedule(), task, launch, null, null));
    }

    @GetMapping("/self-media-schedules/publish-checks/claim-next")
    public R<LocalAgentSelfMediaPublishCheckClaimResponse> claimNextSelfMediaPublishCheck(
            @RequestParam(required = false) String platform,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        SelfMediaPublishScheduleVO schedule =
                scheduleService.claimNextPublishCheckForLocalAgent(
                        session.getOperatorId(),
                        session.getId(),
                        platform,
                        SELF_MEDIA_PUBLISH_CHECK_LOCK_MINUTES
                );
        if (schedule == null) {
            SelfMediaPublishScheduleService.LocalAgentClaimBlock block =
                    scheduleService.consumeLastLocalAgentClaimBlock();
            return R.ok(new LocalAgentSelfMediaPublishCheckClaimResponse(
                    null,
                    null,
                    block == null ? claimBlockedReason(platform) : block.reason(),
                    block == null ? null : block.retryAfterSeconds()
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
        String environmentKey = environment == null ? null : environment.getEnvironmentKey();
        String providerProfileId = environment == null ? null : environment.getProviderProfileId();
        if (!StringUtils.hasText(environmentKey) || !StringUtils.hasText(providerProfileId)) {
            scheduleService.markClaimedPublishCheckUnknown(
                    session.getOperatorId(),
                    session.getId(),
                    schedule.getAttemptCount(),
                    schedule.getId(),
                    diagnosticsJson(
                            "found", false,
                            "reason", "BROWSER_ENVIRONMENT_BINDING_INVALID",
                            "failureCode", "BROWSER_ENVIRONMENT_BINDING_INVALID",
                            "failureMessage", "浏览器环境绑定缺少环境标识或 AdsPower 浏览器编号，已退回等待修复",
                            "scheduleId", schedule.getId(),
                            "browserEnvironmentId", schedule.getBrowserEnvironmentId(),
                            "browserEnvironmentAccountId", schedule.getBrowserEnvironmentAccountId(),
                            "platform", schedule.getPlatform()
                    )
            );
            return R.ok(new LocalAgentSelfMediaPublishCheckClaimResponse(
                    null,
                    null,
                    "BROWSER_ENVIRONMENT_BINDING_INVALID",
                    null
            ));
        }
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
                        null,
                        firstText(
                                environmentAccount == null ? null : environmentAccount.getExpectedAccountName(),
                                account == null ? null : account.getAccountName()
                        ),
                        environmentKey,
                        environment == null ? null : environment.getName(),
                        providerProfileId
                );
        return R.ok(new LocalAgentSelfMediaPublishCheckClaimResponse(schedule, launch, null, null));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/heartbeat")
    public R<SelfMediaPublishScheduleVO> heartbeatSelfMediaSchedule(@PathVariable Long scheduleId,
                                                                    @RequestParam Integer claimAttempt,
                                                                    HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        return R.ok(scheduleService.heartbeatLocalAgentSchedule(
                session.getOperatorId(),
                session.getId(),
                claimAttempt,
                scheduleId,
                SELF_MEDIA_SCHEDULE_LOCK_MINUTES
        ));
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
    public R<Map<String, Object>> markSelfMediaPublishCheckPublished(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String platformPublishedUrl,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markClaimedPublishedConfirmed(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "platformPublishedUrl"), platformPublishedUrl),
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson)
        );
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/publish-checks/unknown")
    public R<Map<String, Object>> markSelfMediaPublishCheckUnknown(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markClaimedPublishCheckUnknown(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson)
        );
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/publish-checks/failed")
    public R<Map<String, Object>> markSelfMediaPublishCheckFailed(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String failureMessage,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markClaimedLocalAgentPublishCheckFailed(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "failureCode"), failureCode),
                firstText(jsonText(body, "failureMessage"), failureMessage),
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson)
        );
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/executions/failed")
    public R<Map<String, Object>> markSelfMediaScheduleExecutionFailed(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String failureCode,
            @RequestParam(required = false) String failureMessage,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markLocalAgentExecutionFailed(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "failureCode"), failureCode),
                firstText(jsonText(body, "failureMessage"), failureMessage),
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson)
        );
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/executions/filled")
    public R<Map<String, Object>> markSelfMediaScheduleExecutionFilled(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markLocalAgentExecutionFilled(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson));
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/executions/scheduled")
    public R<Map<String, Object>> markSelfMediaScheduleExecutionScheduled(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markLocalAgentExecutionScheduled(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson));
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
    }

    @PostMapping("/self-media-schedules/{scheduleId}/executions/published")
    public R<Map<String, Object>> markSelfMediaScheduleExecutionPublished(
            @PathVariable Long scheduleId,
            @RequestParam(required = false) String platformPublishedUrl,
            @RequestParam(required = false) String diagnosticsJson,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        LocalAgentSession session = verifySignedRequest(request);
        scheduleService.markLocalAgentExecutionPublishedConfirmed(
                session.getOperatorId(),
                session.getId(),
                jsonInteger(body, "claimAttempt"),
                scheduleId,
                firstText(jsonText(body, "platformPublishedUrl"), platformPublishedUrl),
                firstText(jsonText(body, "diagnosticsJson"), diagnosticsJson)
        );
        return R.ok(Map.of("ok", true, "scheduleId", scheduleId));
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
        if ("douyin".equalsIgnoreCase(platform)) {
            return "https://creator.douyin.com/creator-micro/content/upload?default-tab=3";
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
            return "https://mp.toutiao.com/profile_v4/graphic/articles";
        }
        if ("xiaohongshu".equalsIgnoreCase(platform)) {
            return "https://creator.xiaohongshu.com/new/note-manager";
        }
        if ("douyin".equalsIgnoreCase(platform)) {
            return "https://creator.douyin.com/creator-micro/content/manage?enter_from=publish";
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
        String appId = resolveBaijiahaoAppId(account);
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        return "https://baijiahao.baidu.com/builder/rc/content"
                + "?currentPage=1&pageSize=10&search=&type=&collection=&app_id="
                + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "&startDate=&endDate=";
    }

    private String resolveBaijiahaoAppId(SelfMediaAccount account) {
        if (account == null) {
            return null;
        }
        String fromExtra = baijiahaoAppIdFromExtraJson(account.getExtraJson());
        if (isValidBaijiahaoAppId(fromExtra)) {
            return fromExtra.trim();
        }
        String platformAccountId = account.getPlatformAccountId();
        if (isValidBaijiahaoAppId(platformAccountId)) {
            return platformAccountId.trim();
        }
        return null;
    }

    private String baijiahaoAppIdFromExtraJson(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            return firstText(
                    root.path("appId").asText(null),
                    root.path("app_id").asText(null),
                    root.path("baijiahaoAppId").asText(null),
                    root.path("baijiahao_app_id").asText(null),
                    root.path("baijiahao").path("appId").asText(null),
                    root.path("baijiahao").path("app_id").asText(null)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidBaijiahaoAppId(String value) {
        return StringUtils.hasText(value) && value.trim().matches("\\d{6,}");
    }

    private String diagnosticsJson(Object... pairs) {
        ObjectNode root = objectMapper.createObjectNode();
        if (pairs == null) {
            return "{}";
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            String key = String.valueOf(pairs[index]);
            Object value = pairs[index + 1];
            if (value == null) {
                root.putNull(key);
            } else if (value instanceof Boolean bool) {
                root.put(key, bool);
            } else if (value instanceof Number number) {
                root.putPOJO(key, number);
            } else {
                root.put(key, String.valueOf(value));
            }
        }
        return root.toString();
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

    private String jsonText(JsonNode body, String fieldName) {
        if (body == null || !body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }
        String value = body.get(fieldName).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer jsonInteger(JsonNode body, String fieldName) {
        if (body == null || !body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = body.get(fieldName);
        if (value.canConvertToInt()) {
            return value.intValue();
        }
        try {
            return Integer.valueOf(value.asText().trim());
        } catch (Exception ignored) {
            return null;
        }
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
